package com.ruoyi.common.exception;

/**
 * Common error code contract for business exceptions.
 */
public interface BaseExceptionInterface
{
    String getErrorCode();

    String getErrorMessage();
}
