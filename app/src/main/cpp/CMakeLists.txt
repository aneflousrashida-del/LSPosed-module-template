cmake_minimum_required(VERSION 3.22.1)
project("speedhack")

find_package(shadowhook REQUIRED CONFIG)
find_library(log-lib log)

add_library(speedhack SHARED speedhack.c)
target_link_libraries(speedhack ${log-lib} m shadowhook::shadowhook)
