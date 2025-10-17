package com.bbthechange.inviter.config;

import com.amazonaws.xray.AWSXRay;
import com.amazonaws.xray.entities.Subsegment;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * AOP aspect that automatically adds X-Ray tracing to all controller and service methods.
 * This provides visibility into the execution time of each layer without modifying individual classes.
 */
@Aspect
@Component
@ConditionalOnProperty(name = "xray.enabled", havingValue = "true", matchIfMissing = false)
public class XRayTracingAspect {

    private static final Logger logger = LoggerFactory.getLogger(XRayTracingAspect.class);

    /**
     * Trace all controller methods to see request handling time
     */
    @Around("execution(* com.bbthechange.inviter.controller..*.*(..))")
    public Object traceController(ProceedingJoinPoint joinPoint) throws Throwable {
        String className = joinPoint.getSignature().getDeclaringType().getSimpleName();
        String methodName = joinPoint.getSignature().getName();
        String segmentName = className + "." + methodName;

        Subsegment subsegment = AWSXRay.beginSubsegment(segmentName);
        try {
            subsegment.putAnnotation("layer", "controller");
            return joinPoint.proceed();
        } catch (Throwable t) {
            subsegment.addException(t);
            throw t;
        } finally {
            AWSXRay.endSubsegment();
        }
    }

    /**
     * Trace all service methods to see business logic execution time
     */
    @Around("execution(* com.bbthechange.inviter.service..*.*(..))")
    public Object traceService(ProceedingJoinPoint joinPoint) throws Throwable {
        String className = joinPoint.getSignature().getDeclaringType().getSimpleName();
        String methodName = joinPoint.getSignature().getName();
        String segmentName = className + "." + methodName;

        Subsegment subsegment = AWSXRay.beginSubsegment(segmentName);
        try {
            subsegment.putAnnotation("layer", "service");
            return joinPoint.proceed();
        } catch (Throwable t) {
            subsegment.addException(t);
            throw t;
        } finally {
            AWSXRay.endSubsegment();
        }
    }
}
