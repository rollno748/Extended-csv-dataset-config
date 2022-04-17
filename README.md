# Extended-csv-dataset-config

## Introduction

This plugins provide additional features over the CSV data set config element for Jmeter. This also provides additional parameterization feature.

This will enable **LoadRunner** users, the privilege of having similar parameter advantage in **Apache JMeter**

## Required Components

1. Apache JMeter components
2. Apache JMeter core

## Jmeter Target

* Jmeter version 5.2.1 or above
* Java 8 or above

## Installation Instructions

* Download the source code from the Github.
* Just do a mvn clean install (M2 is required)
* Jar will be generated under the target directory (di-extended-csv-1.0.jar).
* Copy the Jar to \<Jmeter Installed Directory\>/lib/ext/


## Options

This allows reading of CSV data as follows

* Select Row (Sequential | Random | Unique)
* Update Value (Each Iteration | Once)
* When Out of Values (Continue Cyclic | Continue with last Value | Abort Thread)

The below table is the combinations allowed while using this plugin 

|Select Row|Update value| Out of Values |Allocate Block Size|
| ------ | ------ |------| ------ |
|Sequential|Each Iteration| Continue Cyclic|NA|
|Sequential|Each Iteration| Abort Thread|NA|
|Sequential|Each Iteration| Continue with Last value|NA|
|Sequential|Once| NA   |NA|
|Random|Each Iteration| NA   |NA|
|Random|Once| NA   |NA|
|Unique|Each Iteration| Continue with Last Value |Enabled|
|Unique|Each Iteration| Continue Cyclic |Enabled|
|Unique|Each Iteration| Abort Thread |Enabled|
|Unique|Once| NA   |NA|

## References

 * CSV data set config


## 💲 Donate
<a href="https://www.buymeacoffee.com/rollno748" target="_blank"><img src="https://cdn.buymeacoffee.com/buttons/v2/default-green.png" alt="Buy Me A Coffee" style="max-width:15%;" ></a> 

Please rate a star(:star2:) - If you like it.

Please open up a bug(:beetle:) - If you experience abnormalities.
 
