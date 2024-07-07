package org.linkgems.rical.common.eve.aspect;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.util.Strings;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.linkgems.rical.common.adam.domain.BaseException;
import org.linkgems.rical.common.adam.domain.BaseObject;
import org.linkgems.rical.common.eve.domain.annotation.Log;
import org.linkgems.rical.common.eve.utils.JacksonUtil;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StopWatch;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @description:日志切片
 * @author: meidanlong
 * @date: 2022/11/27 5:05 PM
 */
@Slf4j
@Order(Ordered.HIGHEST_PRECEDENCE)
@Aspect
@Component
public class LogAspect {

    private final static String CLASS_METHOD_NAME = "[%s.%s]";
    private final static String TRACE_TEMP = "%s#%s#%d";
    private final static String OMITTED_MARKER = "...";

    @Pointcut("@annotation(org.linkgems.rical.common.eve.domain.annotation.Log)")
    public void pointCut() {
    }

    @Around("pointCut()")
    public Object pointCut(ProceedingJoinPoint joinPoint) throws Throwable {
        LogDetail logDetail = populateLogDetail(joinPoint);
        String classMethodName = String.format(CLASS_METHOD_NAME, logDetail.getClazz(), logDetail.getMethod());
        // 初始化计时器
        StopWatch timer = new StopWatch();
        timer.start(Thread.currentThread().getId() + "_" + classMethodName + "_" + System.currentTimeMillis());
        try {
            // 执行业务逻辑
            Object result = joinPoint.proceed();
            timer.stop();
            if (!logDetail.getOnlyOnError()) {
                postLog(logDetail, classMethodName, timer, result);
            }
            return result;
        } catch (BaseException bex) {
            timer.stop();
            log.info("!=={} - {} - cost={}ms : businessException={}, check the error.log - params={}", classMethodName, logDetail.getDesc(), timer.getLastTaskTimeMillis(), bex.getMessage(), getObjectStr(logDetail.args));
            log.error("!!!{} - {} : {} - params={}", classMethodName, logDetail.getDesc(), getObjectStr(logDetail.args), exceptionStackTrace(bex), bex);
            Throwable cause = bex.getCause();
            if (cause != null) {
                throw cause;
            } else {
                throw bex;
            }
        } catch (Exception ex) {
            timer.stop();
            log.info("!=={} - {} - cost={}ms : exception={}, check the error.log - params={}", classMethodName, logDetail.getDesc(), timer.getLastTaskTimeMillis(), ex.getMessage(), getObjectStr(logDetail.args));
            log.error("!!!{} - {} : {} - params={}, trace={}", classMethodName, logDetail.getDesc(), ex.getMessage(), getObjectStr(logDetail.args), ex);
            throw ex;
        }
    }

    private void postLog(LogDetail logDetail, String classMethodName, StopWatch timer, Object result) {
        try {
            log.info("<=={} - {} - cost={}ms : result={}, params={}", classMethodName, logDetail.getDesc(), timer.getLastTaskTimeMillis(), getObjectStr(result), getObjectStr(logDetail.args));
        } catch (Exception ex) {
            log.info("<=={} - {} - cost={}ms : can not get result or params, exception={}, check the error.log", classMethodName, logDetail.getDesc(), timer.getLastTaskTimeMillis(), ex.getMessage());
            log.error("!==[LogAspect.postLog] - exception={}", ex.getMessage(), ex);
        } catch (Error err) {
            log.info("<=={} - {} - cost={}ms : can not get result or params, error={}, check the error.log", classMethodName, logDetail.getDesc(), timer.getLastTaskTimeMillis(), err.getMessage());
            log.error("!==[LogAspect.postLog] - error={}", err.getMessage(), err);
        }
    }

    private LogDetail populateLogDetail(ProceedingJoinPoint joinPoint) {
        LogDetail logDetail = new LogDetail();
        try {
            // 通过joinPoint获取被注解方法
            MethodSignature methodSignature = (MethodSignature) joinPoint.getSignature();
            // 1、获取注解参数值
            Log log = methodSignature.getMethod().getAnnotation(Log.class);
            String logDescription = log.description();
            if (StringUtils.isBlank(logDescription)) {
                logDescription = log.value();
                if (StringUtils.isBlank(logDescription)) {
                    logDescription = Strings.EMPTY;
                }
            }
            logDetail.setDesc(logDescription);
            // 2、获取方法/类
            Method method = methodSignature.getMethod();
            logDetail.setMethod(method.getName());
            logDetail.setClazz(method.getDeclaringClass().getSimpleName());
            // 3、获取参数值
            Map<String, Object> argMap = new HashMap<>();
            Object[] args = joinPoint.getArgs();
            //获取运行时参数的名称
            DefaultParameterNameDiscoverer discoverer = new DefaultParameterNameDiscoverer();
            String[] parameterNames = discoverer.getParameterNames(method);
            for (int i = 0; i < parameterNames.length; i++) {
                argMap.put(parameterNames[i], args[i]);
            }
            logDetail.setArgs(getObjectStr(argMap));
            // 4、是否打印
            logDetail.setOnlyOnError(log.onlyOnError());
        } catch (Exception ex) {
            log.info("!== [LogAspect.populateLogDetail] - exception={}, check the error.log", ex.getMessage());
            log.error("!!![LogAspect.populateLogDetail] - exception={}", ex.getMessage(), ex);
        } catch (Error err) {
            log.info("!== [LogAspect.populateLogDetail] - error={}, check the error.log", err.getMessage());
            log.error("!!![LogAspect.populateLogDetail] - error={}", err.getMessage(), err);
        }
        return logDetail;
    }

    private String exceptionStackTrace(Exception e) {
        StackTraceElement[] stackTrace = e.getStackTrace();
        if (stackTrace != null && stackTrace.length > 0) {
            List<String> traceElementList = Arrays.stream(stackTrace).map(st -> String.format(TRACE_TEMP, !StringUtils.isBlank(st.getFileName()) ? st.getFileName().split(".java")[0] : Strings.EMPTY, st.getMethodName(), st.getLineNumber())).collect(Collectors.toList());
            return JacksonUtil.writeValueAsString(traceElementList);
        }
        return Strings.EMPTY;
    }

    private String getObjectStr(Object obj) {
        if (obj instanceof BaseObject) {
            return obj.toString();
        } else {
            return JacksonUtil.writeValueAsString(obj);
        }
    }

    @Getter
    @Setter
    class LogDetail {
        private String clazz = "unknownClass";
        private String method = "unknownMethod";
        private String args = "unknownArgs";
        private String desc = "unknownDesc";
        private Boolean onlyOnError = false;
    }
}
