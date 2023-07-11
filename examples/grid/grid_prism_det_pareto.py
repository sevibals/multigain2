import sys
import random

def generate_grid(size, lra):
    with open("griddet{}_pareto{}.prism".format(str(size), str(lra)), "w") as file:
        grid_size = size**2
        file.write("mdp\n\nmodule grid\n\n\tstate: [0..{}] init 0;\n\n".format(str(grid_size-1)))
        for i in range(0,grid_size):
            state_up = i if i-size < 0 else i-size
            state_left = i if (i%size)-1 < 0 else i-1
            state_right = i if (i%size)==size-1 else i+1
            state_down = i if i+size > grid_size-1 else i+size
            #right_action
            action = "\t[right{}] state={} -> ".format(str(i), str(i))
            action += "(state'={});\n".format(str(state_right))
            file.write(action)

            #down_action
            action = "\t[down{}] state={} -> ".format(str(i), str(i))
            action += "(state'={});\n".format(str(state_down))
            file.write(action)

            #left_action
            action = "\t[left{}] state={} -> ".format(str(i), str(i))
            action += "(state'={});\n".format(str(state_left))
            file.write(action)

            #up_action
            action = "\t[up{}] state={} -> ".format(str(i), str(i))
            action += "(state'={});\n".format(str(state_up))
            file.write(action)

            file.write("\n")
        file.write("endmodule\n\n")
        #file.write("label \"home\" = state=0;\n")
        # a label
        labelless_states = {i for i in range(0,grid_size)}
        a = random.sample(labelless_states, int(grid_size/4))
        label_string = "label \"a\" = "
        for i in a:
            label_string += "state={} | ".format(str(i))
        file.write(label_string[:-3] + ";\n")
        labelless_states = labelless_states.difference(a)
        # b label
        b = random.sample(labelless_states, int(grid_size/4))
        label_string = "label \"b\" = "
        for i in b:
            label_string += "state={} | ".format(str(i))
        file.write(label_string[:-3] + ";\n")
        labelless_states = labelless_states.difference(b)
        # c label
        c = random.sample(labelless_states, int(grid_size/4))
        label_string = "label \"c\" = "
        for i in c:
            label_string += "state={} | ".format(str(i))
        file.write(label_string[:-3] + ";\n")
        labelless_states = labelless_states.difference(c)
        # d label
        label_string = "label \"d\" = "
        for i in labelless_states:
            label_string += "state={} | ".format(str(i))
        file.write(label_string[:-3] + ";\n\n")
        #file.write("label \"tool\" = state={};\n\n".format(str(size**2-1)))
        for i in range(lra):
            file.write("rewards \"default{}\"\n\t".format(str(i)))
            reward_str = ""
            parts = [a, b, c, labelless_states]
            for i in parts[i]:
                reward_str += "state={} | ".format(str(i))
            file.write(reward_str[:-3] + " : 1;\n")
            file.write("endrewards\n")


if __name__ == "__main__":
    generate_grid(int(sys.argv[1]), int(sys.argv[2]))
