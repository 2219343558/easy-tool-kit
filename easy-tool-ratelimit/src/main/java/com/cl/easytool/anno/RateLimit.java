package com.cl.easytool.anno;

import com.cl.easytool.constant.AnnotationType;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.concurrent.TimeUnit;

@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimit {
    String key() default "Limit"; //1.令牌桶Key的前缀
    long intervalPerTokens() default 0;     //2.生成令牌桶的间隔 单位:ms毫秒
    long bucketMaxTokens();       //3.令牌桶的上限
    long resetBucketInterval() default 86400;//4.重置桶内令牌的时间间隔 单位:S 秒 默认为一天
    int time() default 1;//5.时间
    int count() default 1;//6.个数 比如几秒内允许访问几次
    TimeUnit timeType() default TimeUnit.SECONDS;//时间单位
    String type() default AnnotationType.RATE_LIMIT_IP;//限流策略,默认根据ip限流

}
