package com.jaimin.redis.server;

import com.jaimin.redis.command.*;
import com.jaimin.redis.command.CommandDispatcher.Result;
import com.jaimin.redis.protocol.*;
import com.jaimin.redis.store.RedisStore;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

public final class RedisServer implements AutoCloseable {
    private final int port;
    private final ExecutorService workers;
    private final RedisStore store;
    private final AofLog aof;
    private final CommandDispatcher dispatcher;
    private volatile boolean running;
    private ServerSocket serverSocket;

    public RedisServer(int port, int workerCount, Path aofPath, boolean aofEnabled) throws IOException {
        if (port < 1 || port > 65535) throw new IllegalArgumentException("port must be between 1 and 65535");
        if (workerCount < 1) throw new IllegalArgumentException("worker count must be positive");
        this.port=port;
        this.workers=Executors.newFixedThreadPool(workerCount);
        this.store=new RedisStore();
        this.aof=new AofLog(aofPath,aofEnabled);
        this.dispatcher=new CommandDispatcher(store,aof);
        replayAof();
    }

    public void start() throws IOException {
        serverSocket=new ServerSocket(port);
        running=true;
        Runtime.getRuntime().addShutdownHook(new Thread(this::close, "mini-redis-shutdown"));
        System.out.println("Mini Redis listening on port "+port);
        while(running){
            try{
                Socket socket=serverSocket.accept();
                workers.submit(()->handle(socket));
            }catch(SocketException e){if(running)throw e;}
        }
    }

    private void handle(Socket socket){
        try(socket){
            RespDecoder decoder=new RespDecoder(socket.getInputStream());
            OutputStream out=socket.getOutputStream();
            while(running){
                List<String> cmd=decoder.readCommand();
                if(cmd==null)break;
                Result r=dispatcher.execute(cmd,false);
                writeResult(out,r);
                out.flush();
            }
        }catch(Exception ignored){ /* isolate client failures from the server */ }
    }

    private static void writeResult(OutputStream out,Result r)throws IOException{
        switch(r.type()){
            case SIMPLE -> RespEncoder.simple(out,(String)r.value());
            case ERROR -> RespEncoder.error(out,(String)r.value());
            case INTEGER -> RespEncoder.integer(out,(Long)r.value());
            case BULK -> RespEncoder.bulk(out,(String)r.value());
            case ARRAY -> RespEncoder.array(out, castStringList(r.value()));
        }
    }

    private static List<String> castStringList(Object value){
        if(!(value instanceof List<?> list)) throw new IllegalArgumentException("Expected array result");
        List<String> result=new ArrayList<>(list.size());
        for(Object item:list) result.add(String.valueOf(item));
        return result;
    }

    private void replayAof(){
        if(!aof.exists())return;
        try(InputStream in=Files.newInputStream(aof.path())){
            RespDecoder d=new RespDecoder(in);
            List<String> c;
            while((c=d.readCommand())!=null)dispatcher.execute(c,true);
        }catch(Exception e){
            throw new IllegalStateException("AOF replay failed: "+e.getMessage(),e);
        }
    }

    @Override public void close(){
        running=false;
        try{if(serverSocket!=null)serverSocket.close();}catch(IOException ignored){}
        workers.shutdownNow();
        store.close();
        try{aof.close();}catch(IOException ignored){}
    }

    public static void main(String[]args)throws Exception{
        int port=setting("redis.port","REDIS_PORT",6380);
        int workers=setting("redis.workers","REDIS_WORKERS",32);
        boolean enabled=Boolean.parseBoolean(System.getProperty("redis.aof.enabled",System.getenv().getOrDefault("REDIS_AOF_ENABLED","true")));
        Path path=Path.of(System.getProperty("redis.aof",System.getenv().getOrDefault("REDIS_AOF","data/appendonly.aof")));
        new RedisServer(port,workers,path,enabled).start();
    }

    private static int setting(String prop,String env,int def){
        String v=System.getProperty(prop,System.getenv(env));
        try{return v==null?def:Integer.parseInt(v);}catch(NumberFormatException e){return def;}
    }
}