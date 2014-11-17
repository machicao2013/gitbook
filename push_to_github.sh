#!/bin/bash

gitbook build .

test -d book_end || mkdir book_end

cp -r _book/* book_end/

cd book_end

git add .

git commit -a -m "publish"
