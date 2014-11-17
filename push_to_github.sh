#!/bin/bash

gitbook build .

test -d book_end || mkdir book_end

cp -r _book/* book_end/

git checkout gh-pages

git add .

git commit -a -m "publish"
