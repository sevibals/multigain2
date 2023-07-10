#!/usr/bin/env bash

script_dir=$(dirname "$BASH_SOURCE")
cd $script_dir

if ! [ -e ../../results ]
then
  mkdir ../../results
fi
result_dir=../../results/meanpayoff
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
$prism_ex rabin.3.prism rabin.props $gurobi_flag > $result_dir/rabin.3.log
$prism_ex mer.3.prism mer.props $gurobi_flag > $result_dir/mer.3.log
$prism_ex pacman.10.prism pacman.props $gurobi_flag > $result_dir/pacman.10.log
$prism_ex pacman.15.prism pacman.props $gurobi_flag > $result_dir/pacman.15.log
$prism_ex virus.3.prism virus.props $gurobi_flag > $result_dir/virus.3.log
$prism_ex eajs.500.prism eajs.props $gurobi_flag > $result_dir/eajs.500.log
$prism_ex eajs.1000.prism eajs.props $gurobi_flag > $result_dir/eajs.1000.log

