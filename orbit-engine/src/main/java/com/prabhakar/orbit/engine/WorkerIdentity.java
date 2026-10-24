package com.prabhakar.orbit.engine;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.UUID;

import org.springframework.stereotype.Component;

@Component
public class WorkerIdentity {

    private final String id = hostName() + "-" + UUID.randomUUID().toString().substring(0, 6);

    public String id() {
        return id;
    }

    private static String hostName() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            return "unknown-host";
        }
    }
}
