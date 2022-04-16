package com.di.jmeter.utils;

import java.util.ArrayList;
import java.util.List;

public class CSVFileReader {

    private static CSVFileReader instance = null;
    private static List<String> list = new ArrayList<String>();
    private static int autoAllocateBlockSize;


    public static CSVFileReader getInstance(){
        if(instance == null){
            instance = new CSVFileReader();
        }
        return instance;
    }



    public static int getAutoAllocateBlockSize() {
        return autoAllocateBlockSize;
    }

    public static void setAutoAllocateBlockSize(int autoAllocateBlockSize) {
        CSVFileReader.autoAllocateBlockSize = autoAllocateBlockSize;
    }

    public static List<String> getList() {
        return list;
    }

    public static int getListSize(){
        return list.size();
    }

    public static void setList(List<String> list) {
        CSVFileReader.list = list;
    }

    public static void addToList(String line){
        CSVFileReader.list.add(line);
    }

    public String getFromList(int index) {
        return CSVFileReader.list.get(index);
    }

}
