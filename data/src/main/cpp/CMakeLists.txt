cmake_minimum_required(VERSION 3.22.1)

project("chatapp")

add_library(
        chatapp
        SHARED
        api_keys.cpp
)

find_library(
        log-lib
        log
)

target_link_libraries(
        chatapp
        ${log-lib}
)