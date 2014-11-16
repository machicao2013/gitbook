#!/bin/sh

test -f SUMMARY.md || (echo "You must have a file named SUMMARY.md" && exit 1)

for file in `awk -F "[()]" '{print $2}' SUMMARY.md`; do
     path=${file%/*}
     test -d ${path} || mkdir -p $path
     test -f ${file} || touch $file
done
