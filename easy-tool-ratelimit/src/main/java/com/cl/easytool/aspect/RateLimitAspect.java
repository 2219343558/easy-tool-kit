package com.cl.easytool.aspect;

import com.cl.easytool.anno.RateLimit;
import com.alibaba.fastjson2.JSONObject;
import com.cl.easytool.config.MyRedisConfig;
import com.cl.easytool.config.RedisScriptConfig;
import com.cl.easytool.constant.AnnotationType;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Aspect
@Configuration
@Import({MyRedisConfig.class, RedisScriptConfig.class})
public class RateLimitAspect {

    public static final String LOCAL_HOST = "127.0.0.1";
    @Resource(name = "MyRedisTemplate")
    private RedisTemplate redisTemplate;

    @Resource(name = "MyRedisScript")
    private RedisScript<Long> redisScript;



    @Around("@annotation(com.cl.easytool.anno.RateLimit)")
    public Object interceptor(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        Class<?> targetClass = method.getDeclaringClass();
        RateLimit rateLimit = method.getAnnotation(RateLimit.class);
        if (rateLimit != null) {
            HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes()).getRequest();
            String ipAddress = getIpAddr(request);
            StringBuffer stringBuffer = new StringBuffer();
            if (rateLimit.type().equals(AnnotationType.RATE_LIMIT_IP)) {//根据ip限流
                stringBuffer.append(ipAddress).append("_")
                        .append(targetClass.getName()).append("_")
                        .append(method.getName()).append("_")
                        .append(rateLimit.key());
            } else if (rateLimit.type().equals(AnnotationType.RATE_LIMIT_INTERFACE)) {//根据接口限流
                stringBuffer
                        .append(targetClass.getName()).append("_")
                        .append(method.getName()).append("_")
                        .append(rateLimit.key());
            }
            List<String> keys = new ArrayList<>();
            keys.add(stringBuffer.toString().replace(".", "").replace(" ", ""));
            long curTime = System.currentTimeMillis();
            long intervalPerTokens = rateLimit.intervalPerTokens();
            log.info("LimitAspect---KEYS:{},ARGV[1]:{},ARGV[2]:{},ARGV[3]:{},ARGV[4]:{},ARGV[5]:{}", JSONObject.toJSONString(keys)
                    , intervalPerTokens
                    , curTime
                    , rateLimit.bucketMaxTokens()
                    , rateLimit.bucketMaxTokens()
                    , rateLimit.resetBucketInterval());
            if (rateLimit.intervalPerTokens() == 0) {//没设置则自行进行计算令牌投放间隔
                int time = rateLimit.time();
                int count = rateLimit.count();
                TimeUnit timeUnit = rateLimit.timeType();
                BigDecimal ms = BigDecimal.valueOf(1000);
                if (timeUnit == TimeUnit.SECONDS) {
                    intervalPerTokens = Long.parseLong(BigDecimal.valueOf(time).multiply(ms).divide(BigDecimal.valueOf(count)).toString());
                } else if (timeUnit == TimeUnit.MINUTES) {
                    ms = ms.multiply(BigDecimal.valueOf(60));
                    intervalPerTokens = Long.parseLong(BigDecimal.valueOf(time).multiply(ms).divide(BigDecimal.valueOf(count)).toString());
                } else if (timeUnit == TimeUnit.HOURS) {
                    ms = ms.multiply(BigDecimal.valueOf(3600));
                    intervalPerTokens = Long.parseLong(BigDecimal.valueOf(time).multiply(ms).divide(BigDecimal.valueOf(count)).toString());
                } else if (timeUnit == TimeUnit.DAYS) {
                    ms = ms.multiply(BigDecimal.valueOf(86400));
                    intervalPerTokens = Long.parseLong(BigDecimal.valueOf(time).multiply(ms).divide(BigDecimal.valueOf(count)).toString());
                }
            }
            Long execute = (Long) redisTemplate.execute(redisScript, keys
                    , intervalPerTokens              //生成令牌的时间间隔 ms
                    , curTime                        //当前时间 默认
                    , rateLimit.bucketMaxTokens()    //桶初始化时的令牌数 默认为桶上限
                    , rateLimit.bucketMaxTokens()    //令牌桶的上限
                    , rateLimit.resetBucketInterval()//重置桶内令牌的时间间隔  默认为一天
            );
            if (execute != null && execute.intValue() != 0) {
                log.info("LimitAspect---({})拿到令牌了,剩余令牌数量：{} 个", request.getRequestURI(), execute - 1);
                return joinPoint.proceed();
            } else {
                log.info("LimitAspect---({})没拿到令牌,限流啦~~", request.getRequestURI());
                throw new RuntimeException("接口" + request.getRequestURI() + "限流啦,请稍后再试~");
            }
        } else {
            return joinPoint.proceed();
        }
    }

    public static String getIpAddr(HttpServletRequest request) {
        if (request == null) {
            return "unknown";
        }
        String ip = request.getHeader("x-forwarded-for");
        if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Forwarded-For");
        }
        if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }

        if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        return "0:0:0:0:0:0:0:1".equals(ip) ? LOCAL_HOST : ip;
    }
}
