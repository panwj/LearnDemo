package com.mvp.model;

import com.mvp.ben.Apple;

import java.util.ArrayList;

/**
 * Created by panwenjuan on 17-9-26.
 */
public class AppleModel implements IAppleModel {

    private ArrayList list;
    public AppleModel() {
        list = new ArrayList();
        for (int i = 0; i < 10; i++) {
            Apple apple = new Apple();
            apple.setId(i);
            apple.setName("apple " + i);
            apple.setPrice(i);
            list.add(apple);
        }
    }

    @Override
    public Apple load(int id) {
        if (list != null && id < list.size()) {
            return (Apple) list.get(id);
        }
        return null;
    }

    @Override
    public void setAppleName(String name) {

    }

    @Override
    public String getAppleName() {
        return null;
    }

    @Override
    public void setApplePrice(float price) {

    }

    @Override
    public String getApplePrice() {
        return null;
    }
}
