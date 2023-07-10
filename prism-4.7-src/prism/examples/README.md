This file contains some examplary commands to get a better feeling on how to use the program. Traverse into the prism-4.7-src/prism directory before execution.

1. Example contains: LTL, SS, (strategy output)

bin/prism examples/velasquez_paper/figure1.nm examples/velasquez_paper/figure1.props
bin/prism examples/velasquez_paper/figure1.nm examples/velasquez_paper/figure1.props --gurobi
bin/prism examples/velasquez_paper/figure1.nm examples/velasquez_paper/figure1.props --gurobi --exportstrat "policy.txt":"type=actions"
bin/prism examples/velasquez_paper/figure1.nm examples/velasquez_paper/figure1.props --gurobi --exportstrat "policy.txt":"type=dot"

2. Examples from table 1 of CAV submission. Example contains: numerical reward, (boolean reward), LTL, SS
Uncomment/comment lines in examples/grid/grid.props to get different LTL formula and reward structures.

bin/prism examples/grid/grid4.prism examples/grid/grid.props --gurobi
bin/prism examples/grid/grid16.prism examples/grid/grid.props --gurobi
bin/prism examples/grid/grid32.prism examples/grid/grid.props --gurobi
bin/prism examples/grid/grid64.prism examples/grid/grid.props --gurobi

To generate new randomized grid models run the following command with your desired grid size as parameter: 
python3 examples/grid/grid_prism.py 16

3. Example contains: memorybound, LTL, SS

bin/prism examples/membound/membound1_1.prism examples/membound/membound.props --gurobi             (This is example 2 from Jans paper)
bin/prism examples/membound/membound5_3.prism examples/membound/membound.props --gurobi

To generate new membound models run the following command and replaced the two parameters with arbitrary numbers: 
python3 examples/membound/generate_memorybound.py 5 3

4. Examples from table 2 of CAV submission. Examples contain numerical rewards:
   
bin/prism examples/meanpayoff/rabin.3.prism examples/meanpayoff/rabin.props --gurobi
bin/prism examples/meanpayoff/mer.3.prism examples/meanpayoff/mer.props --gurobi
bin/prism examples/meanpayoff/pacman.10.prism examples/meanpayoff/pacman.props --gurobi
bin/prism examples/meanpayoff/pacman.15.prism examples/meanpayoff/pacman.props --gurobi
bin/prism examples/meanpayoff/virus.3.prism examples/meanpayoff/virus.props --gurobi
bin/prism examples/meanpayoff/eajs.500.prism examples/meanpayoff/eajs.props --gurobi
bin/prism examples/meanpayoff/eajs.1000.prism examples/meanpayoff/eajs.props --gurobi

5. Unichain examples. Examples contain (numerical rewards), LTL, SS:
Further information on the models is contained in the .prism files. 
One can also use the unichain query keyword on any of the other examples (e.g. the examples from table 2 of cav submission) by switching out the multi keyword in the .props files

bin/prism examples/unichain/unichain.prism examples/unichain/unichain.props --gurobi
bin/prism examples/unichain/unichain2.prism examples/unichain/unichain2.props --gurobi
bin/prism examples/unichain/unichain_reward.prism examples/unichain/unichain_reward.props --gurobi


6. Detmulti examples. Examples contain LTL, SS:
These examples are directly taken from Ismails paper. The strategy can be exported and compared with the figures in the paper.

bin/prism examples/velasquez_paper/figure1.nm examples/velasquez_paper/figure1.props --gurobi --exportstrat "policy.txt":"type=dot"
bin/prism examples/velasquez_paper/gardentool_agent.nm examples/velasquez_paper/gardentool_agent.props --exportstrat "policy.txt":"type=actions"









