cmake_minimum_required(VERSION 3.10)
project(ZXingCppAndroid)

# The zxing-cpp requires at least C++20 to build
set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

set(ZXING_WRITERS OLD)

add_subdirectory(${CMAKE_CURRENT_SOURCE_DIR}/../../../../../../core ZXing EXCLUDE_FROM_ALL)

add_library(zxingcpp_android SHARED ZxingCpp.cpp)

target_link_options(zxingcpp_android PRIVATE "-Wl,-z,max-page-size=16384")
target_link_libraries(zxingcpp_android PRIVATE ZXing::ZXing jnigraphics)
