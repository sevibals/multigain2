#!/usr/bin/env bash

script_dir=$(dirname "$BASH_SOURCE")
cd $script_dir

if ! [ -e ../../results ]
then
  mkdir ../../results
fi
result_dir=../../results/velasquez_paper
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
$prism_ex figure1.prism figure1.props $gurobi_flag --exportstrat "$result_dir/figure1.policy":"type=dot" > $result_dir/figure1.log
$prism_ex gardentool_agent.prism gardentool_agent_ss075.props $gurobi_flag --exportstrat "$result_dir/gardentool_agent_ss075.policy":"type=actions" > $result_dir/gardentool_agent_ss075.log
$prism_ex gardentool_agent.prism gardentool_agent_ss03.props $gurobi_flag --exportstrat "$result_dir/gardentool_agent_ss03.policy":"type=actions" > $result_dir/gardentool_agent_ss03.log

