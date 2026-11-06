package com.rpc.server.annotation;

import org.junit.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.stereotype.Component;

import static org.junit.Assert.assertTrue;

public class RemoteTest {

    @Test
    public void remoteIsSpringComponentStereotype() {
        assertTrue(
                AnnotatedElementUtils.hasAnnotation(Remote.class, Component.class)
        );
    }

    @Test
    public void springScannerFindsRemoteService() {
        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(true);

        assertTrue(
                scanner.findCandidateComponents("com.rpc.server.remote")
                        .stream()
                        .anyMatch(bean -> "com.rpc.server.remote.UserRemoteImpl"
                                .equals(bean.getBeanClassName()))
        );
    }
}
