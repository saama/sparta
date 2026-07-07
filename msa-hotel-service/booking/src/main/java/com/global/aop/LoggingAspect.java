package com.global.aop;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Arrays;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * API 요청/응답 로깅 AOP
 *
 * <p>컨트롤러 메서드 실행 시 HTTP 메서드, URI, 파라미터, 실행 시간을 기록한다.
 * 예외 발생 시 ERROR 레벨로 스택 트레이스를 기록한다.
 */
@Slf4j
@Aspect
@Component
public class LoggingAspect {

    /**
     * 모든 컨트롤러 메서드에 적용
     *
     * <p>실행 전 요청 정보를 INFO로 기록하고,
     * 실행 후 처리 시간을 DEBUG로 기록한다.
     * 예외 발생 시 ERROR 레벨로 기록한다.
     */
    @Around("execution(* com.domain..controller..*(..))")
    public Object logController(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String methodName = signature.getDeclaringType().getSimpleName() + "." + signature.getName();

        HttpServletRequest request = null;
        ServletRequestAttributes attrs =
            (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs != null) {
            request = attrs.getRequest();
        }

        String httpMethod = request != null ? request.getMethod() : "UNKNOWN";
        String uri = request != null ? request.getRequestURI() : "UNKNOWN";

        log.info("[REQUEST] {} {} | method={} | args={}",
            httpMethod, uri, methodName, Arrays.toString(joinPoint.getArgs()));

        long start = System.currentTimeMillis();
        try {
            Object result = joinPoint.proceed();
            long elapsed = System.currentTimeMillis() - start;
            log.debug("[RESPONSE] {} {} | method={} | elapsed={}ms",
                httpMethod, uri, methodName, elapsed);
            return result;
        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - start;
            log.error("[ERROR] {} {} | method={} | elapsed={}ms | error={}",
                httpMethod, uri, methodName, elapsed, e.getMessage(), e);
            throw e;
        }
    }
}
