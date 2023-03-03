package com.di.jmeter.utils;

import java.util.ArrayList;
import java.util.List;

public class FileReaderExtended {
    private static FileReaderExtended instance = null;
    private static List<String> list = new ArrayList<String>();

    public static FileReaderExtended getInstance(){
        if(instance == null){
            instance = new FileReaderExtended();
        }
        return instance;
    }
}
