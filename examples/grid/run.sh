#!/usr/bin/env bash

script_dir=$(dirname "$BASH_SOURCE")
cd $script_dir

if ! [ -e ../../results ]
then
  mkdir ../../results
fi
result_dir=../../results/grid
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
# Non-deterministic models
$prism_ex grid4.prism grid.props $gurobi_flag > $result_dir/grid4.log
$prism_ex grid16.prism grid.props $gurobi_flag > $result_dir/grid16.log
$prism_ex grid32.prism grid.props $gurobi_flag > $result_dir/grid32.log
if [ -n $gurobi_flag ]
then
 $prism_ex grid64.prism grid.props $gurobi_flag > $result_dir/grid64.log
fi

# Deterministic models
$prism_ex griddet4.prism grid.props $gurobi_flag > $result_dir/griddet4.log
$prism_ex griddet16.prism grid.props $gurobi_flag > $result_dir/griddet16.log
$prism_ex griddet32.prism grid.props $gurobi_flag > $result_dir/griddet32.log
if [ -n $gurobi_flag ]
then
 $prism_ex griddet64.prism grid.props $gurobi_flag > $result_dir/griddet64.log
fi
