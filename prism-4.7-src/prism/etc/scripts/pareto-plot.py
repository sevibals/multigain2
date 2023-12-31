#! /usr/bin/env python
# coding=utf-8

# run without arguments for usage info

import sys
from argparse import ArgumentParser

import matplotlib as mpl
import matplotlib.pyplot as plt
from matplotlib.collections import PolyCollection
from mpl_toolkits.mplot3d import Axes3D
from mpl_toolkits.mplot3d.art3d import Poly3DCollection

descr = "This Python script allows to visualise a 2D and 3D Pareto curve generated by PRISM MultiLongRun."
parser = ArgumentParser(description=descr)
parser.add_argument("filename", help="Pareto curve file exported by PRISM", metavar="FILE")
args = parser.parse_args()
# open input file
try:
    file = open(args.filename)
except IOError as e:
    print("Cannot read the input file '" + e.filename + "': " + e.strerror >> sys.stderr)
    sys.exit(1)

tiles = []
title = ""
objectives = []
vertices = set()
for index, line in enumerate(file):
    if index == 0:
        title = line
        continue
    if index == 1:
        objectives = line.split(',')
        continue
    tile = []
    coords = line.split(';')
    for pointStr in coords:
        point = pointStr.split(',')
        x = float(point[0])
        y = float(point[1])
        if len(point) > 2:
            z = float(point[2])
            vert = (x, y, z)
        else:
            vert = (x, y)
        tile.append(vert)
        vertices.add(vert)
    tiles.append(tile)

# mpl.rcParams['legend.fontsize'] = 50
dim = len(tiles[0][0])
fig = plt.figure(figsize=(10, 10), dpi=100)
if dim == 3:
    ax = Axes3D(fig, auto_add_to_figure=False)
    fig.add_axes(ax)
    ax.set_zlabel(objectives[2], fontsize=25, labelpad=15)
    surfaces = Poly3DCollection(tiles)
    surfaces.set_edgecolor('black')
    ax.add_collection3d(surfaces)
    ax.scatter([pair[0] for pair in vertices], [pair[1] for pair in vertices], [pair[2] for pair in vertices], c='blue')

elif dim == 2:
    ax = fig.add_subplot(111)
    surfaces = PolyCollection(tiles)
    surfaces.set_edgecolor('blue')
    ax.add_collection(surfaces)
    ax.grid(alpha=0.5)
    ax.scatter([pair[0] for pair in vertices], [pair[1] for pair in vertices], c='blue')
else:
    raise ValueError("Can only Plot 2 or 3 dimensional Pareto curves")

ax.set_xlabel(objectives[0], fontsize=25,labelpad=10)
ax.set_ylabel(objectives[1], fontsize=25)
plt.title(title, fontsize=15)
plt.show()
