package com.mvp.view;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.mvp.R;
import com.mvp.presenter.ApplePresenter;

public class AppleView extends AppCompatActivity implements IAppleView {

    private ApplePresenter applePresenter;
    private TextView mID;
    private TextView mName;
    private TextView mPrice;
    private int search = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Log.d("sss", "onCreate()...");
        applePresenter = new ApplePresenter(this);
        mID = (TextView) findViewById(R.id.tv_id);
        mName = (TextView) findViewById(R.id.tv_name);
        mPrice = (TextView) findViewById(R.id.tv_price);

        EditText editText = (EditText) findViewById(R.id.ed_num);
        editText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {

            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                if (!TextUtils.isEmpty(charSequence)) {
                    search = Integer.valueOf(new String(charSequence.toString()));
                }
            }

            @Override
            public void afterTextChanged(Editable editable) {
            }
        });
        findViewById(R.id.btn_search).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                applePresenter.searchApple(search);
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d("sss", "onResume()...");
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.d("sss", "onPause()...");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d("sss", "onDestroy()...");
    }

    @Override
    public void setAppName(String name) {
        if (mName != null) {
            mName.setText(name);
        }
    }

    @Override
    public void setAppPrice(float price) {
        if (mPrice != null) {
            mPrice.setText(String.valueOf(price));
        }
    }

    @Override
    public void setAppId(int id) {
        if (mID != null) {
            mID.setText(String.valueOf(id));
        }
    }

    @Override
    public void searchError() {
        Toast.makeText(getApplicationContext(), "not find", Toast.LENGTH_SHORT).show();
    }
}
