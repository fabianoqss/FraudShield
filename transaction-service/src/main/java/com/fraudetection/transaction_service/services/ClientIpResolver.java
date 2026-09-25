package com.fraudetection.transaction_service.services;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

@Component
public class ClientIpResolver {

    // Trust boundary: local service ports can bypass the gateway. In production a NetworkPolicy
    // must allow only the gateway to reach this service; that makes its rightmost XFF value trusted.
    public String resolve(HttpServletRequest request) {
        var headers = request.getHeaders("X-Forwarded-For");
        String last = null;
        while (headers != null && headers.hasMoreElements()) {
            last = headers.nextElement();
        }
        if (last != null) {
            String ip = last.substring(last.lastIndexOf(',') + 1).trim();
            if (!ip.isEmpty()) return ip;
        }
        return request.getRemoteAddr();
    }
}
