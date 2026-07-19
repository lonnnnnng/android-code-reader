cmake_minimum_required(VERSION 3.24)
project(code_reader LANGUAGES CXX)

add_executable(code_reader main.cpp)
target_compile_features(code_reader PRIVATE cxx_std_20)
