package com.akhimport.narradorestv;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.TextView;

public class MainActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        TextView tv = new TextView(this);
        tv.setText("NarradoresTV funciona!");
        tv.setTextColor(Color.WHITE);
        tv.setBackgroundColor(Color.BLACK);
        tv.setTextSize(28);
        tv.setGravity(Gravity.CENTER);
        setContentView(tv);
    }
}
