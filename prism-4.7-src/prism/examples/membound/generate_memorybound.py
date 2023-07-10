import sys
import random
import math

def generate_memorybound(sizeSS, sizeACC):
    with open("membound{}_{}.prism".format(str(sizeSS),str(sizeACC)), "w") as file:
        file.write("mdp\n\nmodule membound\n\n\tstate: [0..{}] init 0;\n\n".format(str(sizeSS+sizeACC)))
        # SS MEC
        for i in range(0,sizeSS):
            numactions = random.randint(math.ceil(sizeSS/2),sizeSS)
            steadystates = [t for t in range(sizeSS)]
            targets = random.sample(steadystates, numactions)
            for t in targets:
                file.write("\t[ss{}t{}] state={} -> ".format(str(i), str(t), str(i)) +  "(state'={});\n".format(str(t)))
            
        # ACC MEC
        for i in range(sizeSS,sizeSS + sizeACC):
            numactions = random.randint(math.ceil(sizeACC/2),sizeACC)
            acc_states = [t for t in range(sizeSS, sizeSS+sizeACC)]
            targets = random.sample(acc_states, numactions)
            for t in targets:
                file.write("\t[acc{}t{}] state={} -> ".format(str(i), str(t), str(i)) +  "(state'={});\n".format(str(t)))
        # mec to mec transitions
        switch_states = random.sample([s for s in range(sizeSS)], random.randint(1,sizeSS))
        for i in switch_states:
            t = random.randint(sizeSS, sizeSS+sizeACC-1)
            file.write("\t[sw{}t{}] state={} -> ".format(str(i), str(t), str(i)) +  "(state'={});\n".format(str(t)))
        back_states = random.sample([s for s in range(sizeSS, sizeSS+sizeACC)], random.randint(1,sizeACC))
        for i in back_states:
            t = random.randint(0, sizeSS-1)
            file.write("\t[ba{}t{}] state={} -> ".format(str(i), str(t), str(i)) +  "(state'={});\n".format(str(t)))
        # ss label
        file.write("endmodule\n\n")
        label_string = "label \"ss\" = "
        for i in range(sizeSS):
            label_string += "state={} | ".format(str(i))
        file.write(label_string[:-3] + ";\n")
        # acc label
        label_string = "label \"acc\" = "
        for i in range(sizeSS, sizeSS + sizeACC):
            label_string += "state={} | ".format(str(i))
        file.write(label_string[:-3] + ";\n")
    
    
if __name__ == "__main__":
    generate_memorybound(int(sys.argv[1]), int(sys.argv[2]))
 
