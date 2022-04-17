/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.di.jmeter.config;

import org.apache.jmeter.testbeans.BeanInfoSupport;
import org.apache.jmeter.testbeans.gui.FileEditor;
import org.apache.jmeter.testbeans.gui.TypeEditor;
import org.apache.jmeter.util.JMeterUtils;
import org.apache.jorphan.util.JOrphanUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.beans.PropertyDescriptor;

public class ExtendedCsvDataSetBeanInfo extends BeanInfoSupport {

	private static Logger LOGGER = LoggerFactory.getLogger(ExtendedCsvDataSetBeanInfo.class);

	private static final String FILENAME = "filename";
	private static final String FILE_ENCODING = "fileEncoding";
	private static final String VARIABLE_NAMES = "variableNames";
	private static final String IGNORE_FIRST_LINE = "ignoreFirstLine";
	private static final String DELIMITER = "delimiter";
	private static final String QUOTED_DATA = "quotedData";
	private static final String SELECT_ROW = "selectRow"; // Sequential | random | unique
	private static final String UPDATE_VALUE = "updateValue"; // Each iteration | Each occurrence | Once
	private static final String OOVALUE = "ooValue"; // abort Vuser | Continue cyclic manner | continue with lastvalue
	private static final String SHARE_MODE = "shareMode";
	private static final String AUTO_ALLOCATE = "autoAllocate";
	private static final String BLOCK_SIZE = "blockSize";

	private static final String[] SHARE_TAGS = new String[3];
	static final int SHARE_ALL = 0;
	static final int SHARE_GROUP = 1;
	static final int SHARE_THREAD = 2;

	private static final String[] SELECTROW_TAGS = new String[3];
	static final int SEQUENTIAL = 0;
	static final int UNIQUE = 1;
	static final int RANDOM = 2;

	private static final String[] UPDATEVALUE_TAGS = new String[2];
	static final int EACH_ITERATION = 0;
	//static final int EACH_OCCURRENCE = 1;
	static final int ONCE = 1;//2

	private static final String[] RECYCLE_TAGS = new String[3];
	static final int CONTINUE_CYCLIC = 0;
	static final int ABORT_THREAD = 1;
	static final int CONTINUE_WITH_LAST_VALUE = 2;

	// Store the resource keys
	static {
		SELECTROW_TAGS[SEQUENTIAL] = "selectRow.sequential";
		SELECTROW_TAGS[UNIQUE] = "selectRow.unique";
		SELECTROW_TAGS[RANDOM] = "selectRow.random";
		UPDATEVALUE_TAGS[EACH_ITERATION] = "updateValue.eachIteration";
		//UPDATEVALUE_TAGS[EACH_OCCURRENCE] = "updateValue.eachOccurrence";
		UPDATEVALUE_TAGS[ONCE] = "updateValue.once";
		RECYCLE_TAGS[CONTINUE_CYCLIC] = "recycle.continueCyclic";
		RECYCLE_TAGS[ABORT_THREAD] = "recycle.abortThread";
		RECYCLE_TAGS[CONTINUE_WITH_LAST_VALUE] = "recycle.continueLastValue";
		SHARE_TAGS[SHARE_ALL] = "shareMode.all";
		SHARE_TAGS[SHARE_GROUP] = "shareMode.group";
		SHARE_TAGS[SHARE_THREAD] = "shareMode.thread";
	}

	public ExtendedCsvDataSetBeanInfo() {

		super(ExtendedCsvDataSet.class);

		createPropertyGroup("csv_data", new String[] { FILENAME, FILE_ENCODING, VARIABLE_NAMES, IGNORE_FIRST_LINE,
				DELIMITER, QUOTED_DATA, SELECT_ROW, UPDATE_VALUE, OOVALUE, SHARE_MODE });
		createPropertyGroup("block_size", new String[]  { AUTO_ALLOCATE, BLOCK_SIZE });

		PropertyDescriptor p = property(FILENAME);
		p.setValue(NOT_UNDEFINED, Boolean.TRUE);
		p.setValue(DEFAULT, "");
		p.setValue(NOT_EXPRESSION, Boolean.TRUE);
		p.setPropertyEditorClass(FileEditor.class);
		p.setDisplayName("FileName");
		p.setShortDescription("Name of the file that holds the CSV data (relative or absolute filename)");

		p = property(FILE_ENCODING, TypeEditor.ComboStringEditor);
		p.setValue(NOT_UNDEFINED, Boolean.TRUE);
		p.setValue(DEFAULT, "");
		p.setValue(TAGS, getListFileEncoding());
		p.setDisplayName("File Encoding");
		p.setShortDescription("Character set encoding used in file");

		p = property(VARIABLE_NAMES);
		p.setValue(NOT_UNDEFINED, Boolean.TRUE);
		p.setValue(DEFAULT, "");
		p.setValue(NOT_EXPRESSION, Boolean.TRUE);
		p.setDisplayName("Variable Name(s)");
		p.setShortDescription("Varibale name to assign the column");

		p = property(IGNORE_FIRST_LINE);
		p.setValue(NOT_UNDEFINED, Boolean.TRUE);
		p.setValue(DEFAULT, Boolean.FALSE);
		p.setDisplayName("Consider first line as Variable Name");
		p.setShortDescription("Set the value to true, It will consider the first line in CSV as variable name(s)");

		p = property(DELIMITER);
		p.setValue(NOT_UNDEFINED, Boolean.TRUE);
		p.setValue(DEFAULT, ",");
		p.setValue(NOT_EXPRESSION, Boolean.TRUE);
		p.setDisplayName("Delimiter");
		p.setShortDescription("Enter the delimiter ('/t for tab')");

		p = property(QUOTED_DATA);
		p.setValue(NOT_UNDEFINED, Boolean.TRUE);
		p.setValue(DEFAULT, Boolean.FALSE);
		p.setDisplayName("Allow Quoted Data");
		p.setShortDescription("Allow CSV data values to be quoted ?");
		
		p = property(SELECT_ROW, TypeEditor.ComboStringEditor);
		p.setValue(RESOURCE_BUNDLE, getBeanDescriptor().getValue(RESOURCE_BUNDLE));
		p.setValue(NOT_UNDEFINED, Boolean.TRUE);
		p.setValue(DEFAULT, SELECTROW_TAGS[SEQUENTIAL]);
		p.setValue(NOT_OTHER, Boolean.FALSE);
		p.setValue(NOT_EXPRESSION, Boolean.FALSE);
		p.setValue(TAGS, SELECTROW_TAGS);
		p.setDisplayName("Select Row");
		p.setShortDescription("Option to choose the row");

		p = property(UPDATE_VALUE, TypeEditor.ComboStringEditor);
		p.setValue(RESOURCE_BUNDLE, getBeanDescriptor().getValue(RESOURCE_BUNDLE));
		p.setValue(NOT_UNDEFINED, Boolean.TRUE);
		p.setValue(DEFAULT, UPDATEVALUE_TAGS[EACH_ITERATION]);
		p.setValue(NOT_OTHER, Boolean.FALSE);
		p.setValue(NOT_EXPRESSION, Boolean.FALSE);
		p.setValue(TAGS, UPDATEVALUE_TAGS);
		p.setDisplayName("Update Value");
		p.setShortDescription("How you want to update the value");

		p = property(OOVALUE, TypeEditor.ComboStringEditor);
		p.setValue(RESOURCE_BUNDLE, getBeanDescriptor().getValue(RESOURCE_BUNDLE));
		p.setValue(NOT_UNDEFINED, Boolean.TRUE);
		p.setValue(DEFAULT, RECYCLE_TAGS[CONTINUE_CYCLIC]);
		p.setValue(NOT_OTHER, Boolean.FALSE);
		p.setValue(NOT_EXPRESSION, Boolean.FALSE);
		p.setValue(TAGS, RECYCLE_TAGS);
		p.setDisplayName("When out of values");
		p.setShortDescription("Recycle option when hitting EOF");

		p = property(SHARE_MODE, TypeEditor.ComboStringEditor);
		p.setValue(RESOURCE_BUNDLE, getBeanDescriptor().getValue(RESOURCE_BUNDLE));
		p.setValue(NOT_UNDEFINED, Boolean.TRUE);
		p.setValue(DEFAULT, SHARE_TAGS[SHARE_ALL]);
		p.setValue(NOT_OTHER, Boolean.FALSE);
		p.setValue(NOT_EXPRESSION, Boolean.FALSE);
		p.setValue(TAGS, SHARE_TAGS);

		PropertyDescriptor allocateProp = property(AUTO_ALLOCATE);
		allocateProp.setValue(NOT_UNDEFINED, Boolean.TRUE);
		allocateProp.setValue(DEFAULT, Boolean.FALSE);
		allocateProp.setDisplayName("Auto allocate");
		allocateProp.setShortDescription("Auto allocate block size for each thread ");

		allocateProp = property(BLOCK_SIZE);
		allocateProp.setValue(NOT_UNDEFINED, Boolean.TRUE);
		allocateProp.setValue(DEFAULT, "1");
		allocateProp.setValue(NOT_EXPRESSION, Boolean.TRUE);
		allocateProp.setDisplayName("Block size");
		allocateProp.setShortDescription("Allocate block size for each thread ");

	}

	public static int getSelectRowAsInt(String mode) {
		if (mode == null || mode.length() == 0) {
			return SEQUENTIAL; // default (e.g. if test plan does not have definition)
		}
		for (int i = 0; i < SELECTROW_TAGS.length; i++) {
			if (SELECTROW_TAGS[i].equals(mode)) {
				return i;
			}
		}
		return -1;
	}
	
	public static int getUpdateValueAsInt(String mode) {
		if (mode == null || mode.length() == 0) {
			return EACH_ITERATION; // default (e.g. if test plan does not have definition)
		}
		for (int i = 0; i < UPDATEVALUE_TAGS.length; i++) {
			if (UPDATEVALUE_TAGS[i].equals(mode)) {
				return i;
			}
		}
		return -1;
	}
	
	public static int getRecycleAsInt(String mode) {
		if (mode == null || mode.length() == 0) {
			return CONTINUE_CYCLIC; // default (e.g. if test plan does not have definition)
		}
		for (int i = 0; i < RECYCLE_TAGS.length; i++) {
			if (RECYCLE_TAGS[i].equals(mode)) {
				return i;
			}
		}
		return -1;
	}
	
	public static int getShareModeAsInt(String mode) {
		if (mode == null || mode.length() == 0) {
			return SHARE_ALL; // default (e.g. if test plan does not have definition)
		}
		for (int i = 0; i < SHARE_TAGS.length; i++) {
			if (SHARE_TAGS[i].equals(mode)) {
				return i;
			}
		}
		return -1;
	}
	
	public static String[] getShareTags() {
		String[] copy = new String[SHARE_TAGS.length];
		System.arraycopy(SHARE_TAGS, 0, copy, 0, SHARE_TAGS.length);
		return copy;
	}

	public static String[] getSelectRowTags() {
		String[] copy = new String[SELECTROW_TAGS.length];
		System.arraycopy(SELECTROW_TAGS, 0, copy, 0, SELECTROW_TAGS.length);
		return copy;
	}

	public static String[] getUpdateValueTags() {
		String[] copy = new String[UPDATEVALUE_TAGS.length];
		System.arraycopy(UPDATEVALUE_TAGS, 0, copy, 0, UPDATEVALUE_TAGS.length);
		return copy;
	}

	public static String[] getRecycleTags() {
		String[] copy = new String[RECYCLE_TAGS.length];
		System.arraycopy(RECYCLE_TAGS, 0, copy, 0, RECYCLE_TAGS.length);
		return copy;
	}

	 /**
     * Get the mains file encoding
     * list from https://docs.oracle.com/javase/8/docs/technotes/guides/intl/encoding.doc.html
     * @return a String[] with the list of file encoding
     */
	private String[] getListFileEncoding() {
		return JOrphanUtils.split(JMeterUtils.getPropDefault("csvdataset.file.encoding_list", ""), "|");
	}
}
