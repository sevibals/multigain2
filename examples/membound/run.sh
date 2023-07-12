#!/usr/bin/env bash

script_dir=$(dirname "$BASH_SOURCE")
cd $script_dir

if ! [ -e ../../results ]
then
  mkdir ../../results
fi
result_dir=../../results/membound
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
$prism_ex membound1_1.prism membound.props $gurobi_flag --exportstrat "$result_dir/membound1_1.policy":"type=dot" > $result_dir/membound1_1.log
$prism_ex membound2_1.prism membound.props $gurobi_flag --exportstrat "$result_dir/membound2_1.policy":"type=actions" > $result_dir/membound2_1.log

