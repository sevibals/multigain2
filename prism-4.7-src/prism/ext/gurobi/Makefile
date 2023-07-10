################################################
#  NB: This Makefile is designed to be called  #
#      from the main PRISM Makefile. It won't  #
#      work on its own because it needs        #
#      various options to be passed in         #
################################################

PRISM_DIR_REL = ../..
OSLIB=libGurobiJni56.so
JARLIB=gurobi.jar

default: all

all: proxy

# Try and prevent accidental makes (i.e. called manually, not from top-level Makefile)
checks:
#	@if [ "$(LIBSUFFIX)" = "" ]; then \
#	  (echo "Error: This Makefile is designed to be called from the main PRISM Makefile"; exit 1) \
#	fi; 

proxy:
	if [ "$(GUROBI_HOME)" = "" ]; then \
	  echo -n "GUROBI_HOME is not set. Not compiling Gurobi support";\
	else\
	  echo Making gurobi solver proxy ...; \
	  (cd solver && \
	  $(MAKE) \
	  PRISM_CUDD_DIR="$(PRISM_CUDD_DIR)" \
	  PRISM_SRC_DIR="$(PRISM_SRC_DIR)" \
	  PRISM_CLASSES_DIR="$(PRISM_CLASSES_DIR)" \
	  PRISM_OBJ_DIR="$(PRISM_OBJ_DIR)" \
	  PRISM_LIB_DIR="$(PRISM_LIB_DIR)" \
	  PRISM_INCLUDE_DIR="$(PRISM_INCLUDE_DIR)" \
	  JAVA_INCLUDES="$(JAVA_INCLUDES)" \
	  JAVA_JNI_H_DIR="$(JAVA_JNI_H_DIR)" \
	  JAVA_JNI_MD_H_DIR="$(JAVA_JNI_MD_H_DIR)" \
	  CC="$(CC)" \
	  CPP="$(CPP)" \
	  LD="$(LD)" \
	  JAVAC="$(JAVAC) $(JFLAGS)" \
	  JAVACC="$(JAVACC)" \
	  JAVAH="$(JAVAH)" \
	  CFLAGS="$(CFLAGS)" \
	  CPPFLAGS="$(CPPFLAGS)" \
	  LDFLAGS="$(LDFLAGS)" \
	  SHARED="$(SHARED)" \
	  EXE="$(EXE)" \
	  LIBPREFIX="$(LIBPREFIX)" \
	  LIBSUFFIX="$(LIBSUFFIX)" \
	  LIBMATH="$(LIBMATH)" \
	  CLASSPATHSEP="$(CLASSPATHSEP)") \
	  || exit 1;\
	fi;

clean: checks

celan:	clean


#################################################
