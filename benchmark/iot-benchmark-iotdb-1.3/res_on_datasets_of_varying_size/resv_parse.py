import os
import numpy as np



types = ["AGG_RANGE","AGG_VALUE","EXP_VALUE","AGG_RANGE_VALUE","VALUE_RANGE"]

def write_res_line(outfile,res_matrix,type_idx):
    res_data = res_matrix[type_idx]
    outfile.write(types[type_idx])
    for i in [0,1,2,3]:
        outfile.write(","+str(res_data[i]))
    outfile.write(","+str(round(((res_data[1]-res_data[0])/res_data[1]),4)))
    outfile.write(","+str(round(((res_data[2]-res_data[0])/res_data[2]),4)))
    outfile.write("\n")

def write_res(outfile,tag,res_matrix):
    print("res_matrix = ",res_matrix)
    outfile.write(tag+",modi,baseline,plain,nolazy,ratio,ratio_p\n")
    for i in range(len(res_matrix)):
        write_res_line(outfile,res_matrix,i)



def calculate_res(path,tag,outfile): 
    infile = open(path,'r')
    content = infile.readlines()
    AGG_RANGE = []
    AGG_VALUE = []
    AGG_RANGE_VALUE = []
    EXP_VALUE = []
    VALUE_RANGE = []
    toskip = 0
    for i in range(len(content)):
        if toskip >0:
            toskip -= 1
            continue
        line = content[i]
        if(line.startswith("----------------------------------------------------------Result Matrix")):
            toskip= 14
            continue
        if(line.startswith("--------------------------------------------------------------------------Latency (ms) Matrix")):
            continue
        if(line.startswith("AGG_RANGE_VALUE")):
            line = line.strip('\n').strip('s').strip(" ")
            for i in range(40):
                line = line.replace("  "," ")
            AGG_RANGE_VALUE.append((float)(line.split(' ')[1]))
            print("AGG_RANGE_VALUE",(float)(line.split(' ')[1]))
            continue
        elif (line.startswith("EXP_VALUE")):
            line = line.strip('\n').strip('s').strip(" ")
            for i in range(40):
                line = line.replace("  "," ")
            EXP_VALUE.append((float)(line.split(' ')[1]))
            print("EXP_VALUE",(float)(line.split(' ')[1]))
            continue
        elif (line.startswith("AGG_RANGE")):
            line = line.strip('\n').strip('s').strip(" ")
            for i in range(40):
                line = line.replace("  "," ")
            AGG_RANGE.append((float)(line.split(' ')[1]))
            print("AGG_RANGE",(float)(line.split(' ')[1]))
            continue
        elif (line.startswith("AGG_VALUE")):
            line = line.strip('\n').strip('s').strip(" ")
            for i in range(40):
                line = line.replace("  "," ")
            AGG_VALUE.append((float)(line.split(' ')[1]))
            print("AGG_VALUE",(float)(line.split(' ')[1]))
    res_matrix = [AGG_RANGE,AGG_VALUE,EXP_VALUE,AGG_RANGE_VALUE]
    write_res(outfile,tag,res_matrix)
    return


def travers_calc():
    out_path = "./statics.csv"
    outfile = open(out_path,'w')
    for filepath,dirnames,filenames in os.walk(r"./"):
        if(len(dirnames)>1):
            continue
        for filename in filenames:
            respath = os.path.join(filepath,filename)
            resname = filepath.split('/')[-1]+filename
            if(not filename.startswith("res_parse") and not filename.startswith("statics") and not filename.startswith("resv_parse")):
                print(respath)
                calculate_res(respath,resname,outfile)
            
travers_calc()
