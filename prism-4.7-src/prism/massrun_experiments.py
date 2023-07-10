import os

gridsizes = [64]
for i in range(1):
    for j in range(5):
        os.system("python3 examples/grid/grid_prism_det.py {}".format(str(gridsizes[i])))
        os.system("bin/prism griddet{}.prism examples/grid/grid.props".format(str(gridsizes[i])))
    #os.system("mv times.txt /home/sevi/Informatik/22WS/masterarbeit/gridtimes/lpsolve/theta2/theta2times{}.txt".format(str(gridsizes[i])))