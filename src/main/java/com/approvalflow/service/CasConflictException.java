package com.approvalflow.service;

public class CasConflictException extends RuntimeException {
    public CasConflictException(String instanceId, long expectedVersion, long actualVersion) {
        super("CAS conflict for instance " + instanceId + ": expected " + expectedVersion + ", actual " + actualVersion);
    }
}
