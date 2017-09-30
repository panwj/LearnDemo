package com.mvp.model;

import com.mvp.ben.Apple;

/**
 * Created by panwenjuan on 17-9-26.
 */
public interface IAppleModel {
    void setAppleName(String name);
    String getAppleName();
    void setApplePrice(float price);
    String getApplePrice();
    Apple load(int id);
}
