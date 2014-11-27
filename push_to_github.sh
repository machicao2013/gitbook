#!/bin/bash

test -d _book && rm -rf _book/*

gitbook build .

test -d book_end || mkdir book_end

cp -r _book/* book_end/

cd book_end

git pull origin gh-pages

git add .

git commit -a -m "publish"

git push origin gh-pages
