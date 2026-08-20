cmake_minimum_required(VERSION 3.22.1)
project(codec_pty C)

add_library(codec-pty SHARED pty.c)

find_library(log-lib log)
target_link_libraries(codec-pty ${log-lib})

target_compile_options(codec-pty PRIVATE
  -std=c11
  -fvisibility=hidden
  -Wall
  -Wextra
  -Werror=implicit-function-declaration
)
