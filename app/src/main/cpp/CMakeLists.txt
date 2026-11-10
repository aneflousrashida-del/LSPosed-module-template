cmake_minimum_required(VERSION 3.22.1)
project("speedhack")

add_library(speedhack SHARED speedhack.c)

find_library(log-lib log)
target_link_libraries(speedhack ${log-lib} m)
