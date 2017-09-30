package com.mvp.presenter;

import com.mvp.ben.Apple;
import com.mvp.model.AppleModel;
import com.mvp.model.IAppleModel;
import com.mvp.view.IAppleView;

/**
 * Created by panwenjuan on 17-9-26.
 */
public class ApplePresenter {

    private IAppleModel appleModel;
    private IAppleView appleView;

    public ApplePresenter(IAppleView view) {
        appleView = view;
        appleModel = new AppleModel();
    }

    public void searchApple(int id) {
        if (appleModel != null && appleView != null) {
            Apple apple = appleModel.load(id);
            if (apple == null) {
                appleView.searchError();
                return;
            }
            appleView.setAppId(apple.getId());
            appleView.setAppName(apple.getName());
            appleView.setAppPrice(apple.getPrice());
        }
    }

}
