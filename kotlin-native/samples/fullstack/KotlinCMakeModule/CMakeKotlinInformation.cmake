if (CMAKE_USER_MAKE_RULES_OVERRIDE)
    # Save the full path of the file so try_compile can use it.
    include(${CMAKE_USER_MAKE_RULES_OVERRIDE} RESULT_VARIABLE _override)
    set(CMAKE_USER_MAKE_RULES_OVERRIDE "${_override}")
endif ()

if (CMAKE_USER_MAKE_RULES_OVERRIDE_Kotlin)
    # Save the full path of the file so try_compile can use it.
    include(${CMAKE_USER_MAKE_RULES_OVERRIDE_Kotlin} RESULT_VARIABLE _override)
    set(CMAKE_USER_MAKE_RULES_OVERRIDE_Kotlin "${_override}")
endif ()

if (NOT CMAKE_Kotlin_COMPILE_OBJECT)
    set(CMAKE_Kotlin_COMPILE_OBJECT "")
    # more native CMake without cinterop would be:
    # set(CMAKE_Kotlin_COMPILE_OBJECT "$(CMAKE_COMMAND) -E copy <SOURCE> <OBJECT>")
endif ()

if (NOT CMAKE_Kotlin_LINK_EXECUTABLE)
    set(CMAKE_Kotlin_LINK_EXECUTABLE "")
    # more native CMake without cinterop would be:
    # set(CMAKE_Kotlin_LINK_EXECUTABLE "${CMAKE_Kotlin_COMPILER} <OBJECTS> -o <TARGET>")
endif ()

set(CMAKE_Kotlin_FLAGS "" CACHE STRING
        "Flags used by the compiler during all build types.")
set(CMAKE_Kotlin_FLAGS_DEBUG "-g" CACHE STRING
        "Flags used by the compiler during debug builds.")
set(CMAKE_Kotlin_FLAGS_MINSIZEREL "-opt" CACHE STRING
        "Flags used by the compiler during release builds for minimum size.")
set(CMAKE_Kotlin_FLAGS_RELEASE "-opt" CACHE STRING
        "Flags used by the compiler during release builds.")
set(CMAKE_Kotlin_FLAGS_RELWITHDEBINFO "-opt -g" CACHE STRING
        "Flags used by the compiler during release builds with debug info.")

set(CMAKE_Kotlin_LIBRARY_DIR  ${CMAKE_CURRENT_BINARY_DIR}${CMAKE_FILES_DIRECTORY}/libs/)

mark_as_advanced(
        CMAKE_Kotlin_FLAGS
        CMAKE_Kotlin_FLAGS_DEBUG
        CMAKE_Kotlin_FLAGS_RELEASE
)

function(cinterop)
    cmake_parse_arguments(
            CINTEROP
            ""
            "NAME;DEF_FILE;TARGET"
            "LIBRARIES;COMPILER_OPTS"
            ${ARGN}
    )

    if (NOT CINTEROP_NAME)
        message(FATAL_ERROR "You must provide a name")
    endif ()

    if (NOT CINTEROP_DEF_FILE)
        message(FATAL_ERROR "You must provide def file location")
    endif ()

    if (CINTEROP_TARGET)
        set(TARGET_FLAG -target CINTEROP_TARGET)
    elseif (APPLE)
        set(TARGET_FLAG -target macbook)
    elseif (UNIX)
        set(TARGET_FLAG -target linux)
    else ()
        message(FATAL_ERROR "Unsupported host target")
    endif ()

    set(COMPILER_OPTS_FLAG)
    foreach (COMPILER_OPT ${CINTEROP_COMPILER_OPTS})
        set(COMPILER_OPTS_FLAG ${COMPILER_OPTS_FLAG} -compilerOpts ${COMPILER_OPT})
    endforeach ()

    set(LIBRARY_FLAG)
    foreach (LIBRARY ${CINTEROP_LIBRARIES})
        set(LIBRARY_FLAG ${LIBRARY_FLAG} -library ${LIBRARY})
    endforeach()

    set(LIBRARY_OUTPUT ${CMAKE_Kotlin_LIBRARY_DIR}/${CINTEROP_NAME}.klib)
    add_custom_command(
            OUTPUT ${LIBRARY_OUTPUT}
            DEPENDS ${CINTEROP_DEF_FILE} ${CINTEROP_LIBRARIES}
            COMMAND ${CMAKE_Kotlin_CINTEROP} ${COMPILER_OPTS_FLAG} ${LIBRARY_FLAG}
                    -def ${CMAKE_CURRENT_SOURCE_DIR}/${CINTEROP_DEF_FILE} ${TARGET_FLAG}
                    -r ${CMAKE_Kotlin_LIBRARY_DIR} -o ${LIBRARY_OUTPUT}
    )
    set(INTEROP_GENERATED_SOURCE ${CMAKE_Kotlin_LIBRARY_DIR}/${CINTEROP_NAME}-build/kotlin/${CINTEROP_NAME}/${CINTEROP_NAME}.kt)
    if(NOT EXISTS ${INTEROP_GENERATED_SOURCE})
        file(WRITE ${INTEROP_GENERATED_SOURCE} "")
    endif()
    add_custom_target(${CINTEROP_NAME}
            DEPENDS ${LIBRARY_OUTPUT}
            SOURCES ${CINTEROP_DEF_FILE} ${INTEROP_GENERATED_SOURCE})
        
    foreach (LIBRARY ${CINTEROP_LIBRARIES})
        add_dependencies(${CINTEROP_NAME} ${LIBRARY})
    endforeach()
    
endfunction()

include(CMakeParseArguments)

macro(prepare_kotlinc_args)
    cmake_parse_arguments(
            KOTLINC
            ""
            "NAME;TARGET"
            "SOURCES;LIBRARIES;LINKER_OPTS"
            ${ARGN}
    )

    if (NOT KOTLINC_NAME)
        message(FATAL_ERROR "You must provide a name")
    endif ()

    if (NOT KOTLINC_SOURCES)
        message(FATAL_ERROR "You must provide list of sources")
    endif ()

    if (KOTLINC_TARGET)
        set(TARGET_FLAG -target KOTLINC_TARGET)
    elseif (APPLE)
        set(TARGET_FLAG -target macbook)
    elseif (UNIX)
        set(TARGET_FLAG -target linux)
    else ()
        message(FATAL_ERROR "Unsupported host target")
    endif ()

    set(LINKER_OPTS_FLAG)
    if (KOTLINC_LINKER_OPTS)
        foreach (LINKER_OPT ${KOTLINC_LINKER_OPTS})
            set(LINKER_OPTS_FLAG ${LINKER_OPTS_FLAG} -linkerOpts ${LINKER_OPT})
        endforeach ()
    endif ()

    set(LIBRARY_PATH)
    foreach (KOTLINC_LIBRARY ${KOTLINC_LIBRARIES})
        set(LIBRARY_PATH ${LIBRARY_PATH} -library ${KOTLINC_LIBRARY})
    endforeach ()

    set(ADDITIONAL_KOTLINC_FLAGS ${CMAKE_Kotlin_FLAGS})
    if (CMAKE_BUILD_TYPE STREQUAL "Debug")
        string(APPEND ADDITIONAL_KOTLINC_FLAGS " ${CMAKE_Kotlin_FLAGS_DEBUG}")
    elseif (CMAKE_BUILD_TYPE STREQUAL "MinSizeRel")
        string(APPEND ADDITIONAL_KOTLINC_FLAGS " ${CMAKE_Kotlin_FLAGS_MINSIZEREL}")
    elseif (CMAKE_BUILD_TYPE STREQUAL "Release")
        string(APPEND ADDITIONAL_KOTLINC_FLAGS " ${CMAKE_Kotlin_FLAGS_RELEASE}")
    elseif (CMAKE_BUILD_TYPE STREQUAL "RelWithDebInfo")
        string(APPEND ADDITIONAL_KOTLINC_FLAGS " ${CMAKE_Kotlin_FLAGS_RELWITHDEBINFO}")
    endif()
    separate_arguments(ADDITIONAL_KOTLINC_FLAGS)
endmacro()

function(kotlinc)
    prepare_kotlinc_args(${ARGV})

    set(KOTLINC_${KOTLINC_NAME}_EXECUTABLE_PATH ${CMAKE_CURRENT_BINARY_DIR}${CMAKE_FILES_DIRECTORY}/${KOTLINC_NAME})

    add_custom_command(
            OUTPUT ${KOTLINC_${KOTLINC_NAME}_EXECUTABLE_PATH}_TEMP.kexe
            DEPENDS ${KOTLINC_SOURCES}
            COMMAND ${CMAKE_Kotlin_COMPILER} ${ADDITIONAL_KOTLINC_FLAGS} ${KOTLINC_SOURCES}
                    ${LIBRARY_PATH} ${TARGET_FLAG} ${LINKER_OPTS_FLAG} -r ${CMAKE_Kotlin_LIBRARY_DIR}
                    -o ${KOTLINC_${KOTLINC_NAME}_EXECUTABLE_PATH}_TEMP
            COMMAND rm -f ${CMAKE_CURRENT_BINARY_DIR}/${KOTLINC_NAME}.kexe
            WORKING_DIRECTORY ${CMAKE_CURRENT_SOURCE_DIR}
    )

    add_custom_target(${KOTLINC_NAME}.compile
            DEPENDS ${KOTLINC_${KOTLINC_NAME}_EXECUTABLE_PATH}_TEMP.kexe
            SOURCES ${KOTLINC_SOURCES})

    foreach (KOTLINC_LIBRARY ${KOTLINC_LIBRARIES})
        add_dependencies(${KOTLINC_NAME}.compile ${KOTLINC_LIBRARY})
    endforeach ()

    add_executable(${KOTLINC_NAME}.kexe ${KOTLINC_SOURCES})
    add_dependencies(${KOTLINC_NAME}.kexe ${KOTLINC_NAME}.compile)
    set_target_properties(${KOTLINC_NAME}.kexe PROPERTIES LINKER_LANGUAGE Kotlin)
    add_custom_command(TARGET ${KOTLINC_NAME}.kexe
            PRE_LINK
            COMMAND ${CMAKE_COMMAND} -E copy ${KOTLINC_${KOTLINC_NAME}_EXECUTABLE_PATH}_TEMP.kexe ${CMAKE_CURRENT_BINARY_DIR}/${KOTLINC_NAME}.kexe)

endfunction()

function(kotlinc_library)
    prepare_kotlinc_args(${ARGV})

    set(LIBRARY_OUTPUT ${CMAKE_Kotlin_LIBRARY_DIR}/${KOTLINC_NAME}.klib)
    add_custom_command(
            OUTPUT ${LIBRARY_OUTPUT}
            DEPENDS ${KOTLINC_SOURCES}
            COMMAND ${CMAKE_COMMAND} -E make_directory ${CMAKE_Kotlin_LIBRARY_DIR}
            COMMAND ${CMAKE_Kotlin_COMPILER} -produce library ${ADDITIONAL_KOTLINC_FLAGS}
                    ${KOTLINC_SOURCES} ${LIBRARY_PATH} ${TARGET_FLAG} ${LINKER_OPTS_FLAG}
                    -r ${CMAKE_Kotlin_LIBRARY_DIR} -o ${LIBRARY_OUTPUT}
            WORKING_DIRECTORY ${CMAKE_CURRENT_SOURCE_DIR}
    )

    add_custom_target(${KOTLINC_NAME}
            DEPENDS ${LIBRARY_OUTPUT}
            SOURCES ${KOTLINC_SOURCES})

    foreach (KOTLINC_LIBRARY ${KOTLINC_LIBRARIES})
        add_dependencies(${KOTLINC_NAME} ${KOTLINC_LIBRARY})
    endforeach ()

    set_target_properties(${KOTLINC_NAME} PROPERTIES LINKER_LANGUAGE Kotlin)
endfunction()