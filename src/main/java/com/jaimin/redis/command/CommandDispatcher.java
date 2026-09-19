package com.jaimin.redis.command;

import com.jaimin.redis.store.*;
import java.util.*;

public final class CommandDispatcher {
    private final RedisStore store;
    private final AofLog aof;
    public CommandDispatcher(RedisStore store, AofLog aof) { this.store=store; this.aof=aof; }

    public Result execute(List<String> args, boolean replay) {
        if(args==null||args.isEmpty()) return Result.error("empty command");
        String cmd=args.get(0).toUpperCase(Locale.ROOT);
        try {
            Result result=switch(cmd) {
                case "PING" -> args.size()==1 ? Result.simple("PONG") : Result.bulk(args.get(1));
                case "ECHO" -> require(args,2,2,()->Result.bulk(args.get(1)));
                case "SET" -> set(args);
                case "GET" -> get(args);
                case "DEL" -> del(args);
                case "EXISTS" -> exists(args);
                case "INCR" -> mutateIncrement(args,1);
                case "DECR" -> mutateIncrement(args,-1);
                case "EXPIRE" -> expire(args);
                case "TTL" -> ttl(args);
                case "LPUSH" -> push(args,true);
                case "RPUSH" -> push(args,false);
                case "LPOP" -> pop(args,true);
                case "RPOP" -> pop(args,false);
                case "LRANGE" -> range(args);
                case "DBSIZE" -> Result.integer(store.size());
                default -> Result.error("unknown command '"+args.get(0)+"'");
            };
            if(!replay && result.mutating && result.ok) aof.append(args);
            return result;
        } catch(IllegalArgumentException e){ return Result.error(e.getMessage()); }
        catch(Exception e){ return Result.error("internal error"); }
    }

    private Result set(List<String>a){
        if(a.size()<3) return Result.error("wrong number of arguments for 'set'");
        store.put(a.get(1),new StringValue(a.get(2)));
        return Result.simple("OK",true);
    }
    private Result get(List<String>a){ if(a.size()!=2)return Result.error("wrong number of arguments for 'get'"); Value v=store.get(a.get(1)); if(v==null)return Result.bulk(null); if(!(v instanceof StringValue s))throw new IllegalArgumentException("WRONGTYPE key is not a string"); return Result.bulk(s.get()); }
    private Result del(List<String>a){ if(a.size()<2)return Result.error("wrong number of arguments for 'del'"); long n=0; for(int i=1;i<a.size();i++)if(store.remove(a.get(i))!=null)n++; return Result.integer(n,true); }
    private Result exists(List<String>a){ if(a.size()<2)return Result.error("wrong number of arguments for 'exists'"); long n=0; for(int i=1;i<a.size();i++)if(store.exists(a.get(i)))n++; return Result.integer(n); }
    private Result mutateIncrement(List<String>a,long d){ if(a.size()!=2)return Result.error("wrong number of arguments"); return Result.integer(store.increment(a.get(1),d),true); }
    private Result expire(List<String>a){ if(a.size()!=3)return Result.error("wrong number of arguments for 'expire'"); long s=parseLong(a.get(2)); return Result.integer(store.expire(a.get(1),s) ? 1 : 0,true); }
    private Result ttl(List<String>a){ if(a.size()!=2)return Result.error("wrong number of arguments for 'ttl'"); return Result.integer(store.ttl(a.get(1))); }
    private Result push(List<String>a,boolean left){ if(a.size()<3)return Result.error("wrong number of arguments"); List<String> items=a.subList(2,a.size()); long n=left?store.pushLeft(a.get(1),items):store.pushRight(a.get(1),items); return Result.integer(n,true); }
    private Result pop(List<String>a,boolean left){ if(a.size()!=2)return Result.error("wrong number of arguments"); String v=left?store.popLeft(a.get(1)):store.popRight(a.get(1)); return Result.bulk(v,true); }
    private Result range(List<String>a){ if(a.size()!=4)return Result.error("wrong number of arguments"); return Result.array(store.range(a.get(1),parseLong(a.get(2)),parseLong(a.get(3)))); }
    private static long parseLong(String s){ try{return Long.parseLong(s);}catch(NumberFormatException e){throw new IllegalArgumentException("value is not an integer");} }
    private static Result require(List<String>a,int min,int max,Supplier<Result> s){if(a.size()<min||a.size()>max)return Result.error("wrong number of arguments");return s.get();}
    @FunctionalInterface interface Supplier<T>{T get();}

    public record Result(Type type,Object value,boolean ok,boolean mutating){
        public enum Type{SIMPLE,ERROR,INTEGER,BULK,ARRAY}
        static Result simple(String v){return simple(v,false);} static Result simple(String v,boolean m){return new Result(Type.SIMPLE,v,true,m);}
        static Result error(String v){return new Result(Type.ERROR,v,false,false);} static Result integer(long v){return integer(v,false);} static Result integer(long v,boolean m){return new Result(Type.INTEGER,v,true,m);}
        static Result bulk(String v){return bulk(v,false);} static Result bulk(String v,boolean m){return new Result(Type.BULK,v,true,m);} static Result array(List<String>v){return new Result(Type.ARRAY,v,true,false);}
    }
}