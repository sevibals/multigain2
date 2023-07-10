@echo off

xcopy ..\src\java\lpsolve\*.class lpsolve\ /y
rem jar -c -f ..\lib\lpsolve55j.jar *.class lpsolve
jar cvf ..\lib\lpsolve55j.jar *.class lpsolve

javac -classpath ..\lib\lpsolve55j.jar Demo2.java
rem jar -c -f Demo2.jar Demo2.class
jar cvf Demo2.jar Demo2.class
rem following shows contents of jar file.
rem rem jar -t -f Demo2.jar
rem jar tf Demo2.jar

javac -classpath ..\lib\lpsolve55j.jar;..\lib\junit.jar LpSolveTest.java
rem jar -c -f unittests.jar LpSolveTest.class LpSolveTest$1MyListener.class LpSolveTest$2MyListener.class LpSolveTest$3MyListener.class
jar cvf unittests.jar LpSolveTest.class LpSolveTest$1MyListener.class LpSolveTest$2MyListener.class LpSolveTest$3MyListener.class

