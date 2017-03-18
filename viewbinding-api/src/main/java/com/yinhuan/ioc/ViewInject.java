package com.yinhuan.ioc;


public interface ViewInject<T>
{
    void inject(T t, Object source);
}
