#!/usr/bin/env bash

script_dir=$(dirname "$BASH_SOURCE")
cd $script_dir

if ! [ -e ../../results ]
then
  mkdir ../../results
fi
result_dir=../../results/thesis
if ! [ -e $result_dir ]
then
  mkdir $result_dir
fi
prism_ex=../../prism-4.7-src/prism/bin/prism
if [ -e ../../bin/multigain2 ]
then
  prism_ex=../../bin/multigain2
fi

gurobi_flag=''
if ! [ -z "$GUROBI_HOME" ]
then
 gurobi_flag=--gurobi
fi
$prism_ex thesis.prism thesis_multi.props $gurobi_flag --exportstrat "$result_dir/thesis_multi.policy":"type=dot" > $result_dir/thesis_multi.log
$prism_ex thesis.prism thesis_mlessmulti.props $gurobi_flag --exportstrat "$result_dir/thesis_mlessmulti.policy":"type=dot" > $result_dir/thesis_mlessmulti.log
