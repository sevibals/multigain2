#!/usr/bin/env bash

script_dir=$(dirname "$BASH_SOURCE")
cd $script_dir

if ! [ -e ../../results ]
then
  mkdir ../../results
fi
result_dir=../../results/unichain
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
$prism_ex unichain.prism unichain.props $gurobi_flag > $result_dir/unichain.log
$prism_ex unichain2.prism unichain2.props $gurobi_flag > $result_dir/unichain2.log
$prism_ex unichain_reward.prism unichain_reward.props $gurobi_flag > $result_dir/unichain_reward.log

