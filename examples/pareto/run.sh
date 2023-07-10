#!/usr/bin/env bash

script_dir=$(dirname "$BASH_SOURCE")
cd $script_dir

if ! [ -e ../../results ]
then
  mkdir ../../results
fi
result_dir=../../results/pareto
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
$prism_ex pareto2d.nm pareto2d.props --exportpareto $result_dir/pareto2d.pareto $gurobi_flag > $result_dir/pareto2d.log
$prism_ex pareto3d.nm pareto3d.props --exportpareto $result_dir/pareto3d.pareto $gurobi_flag > $result_dir/pareto3d.log

python ../../prism-4.7-src/prism/etc/scripts/pareto-plot.py $result_dir/pareto2d.pareto
python ../../prism-4.7-src/prism/etc/scripts/pareto-plot.py $result_dir/pareto3d.pareto
