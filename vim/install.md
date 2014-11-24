vim的安装
=========

首先安装依赖
```sh
#!/bin/bash

if [[ "a`whoami`" != "aroot" ]]; then
    echo "You Must Be root"
    exit 1
fi

yum install -y ncurses-devel ruby ruby-devel lua lua-devel luajit luajit-devel ctags mercurial python python-devel python3 \ python3-devel tcl-devel perl perl-devel perl-ExtUtils-ParseXS perl-ExtUtils-XSpp perl-ExtUtils-CBuilder perl-ExtUtils-Embed cmake
```

因为我使用的YouCompleteMe等插件，vim不能简单的使用yum或者apt-get安装，这里提供一个脚本。该脚本包含了插件的安装.
```sh
#!/bin/bash

base_dir=`pwd`
vim_install_dir=/home/machicao/opt/vim74a

test -d ${vim_install_dir} || mkdir -p ${vim_install_dir}

test -d ~/.vim/bundle/vundle || mkdir -p ~/.vim/bundle/vundle

git clone https://github.com/gmarik/vundle.git ~/.vim/bundle/vundle

if [[! -f ./vim-7.4a.tar.bz2 ]]; then
	wget ftp://ftp.vim.org/pub/vim/unstable/unix/vim-7.4a.tar.bz2
fi

if [[! -f ./vim-7.4a.tar.bz2 ]]; then
	echo "./vim-7.4a.tar.bz2 does not exists"
	exit 1
fi

tar jxvf ./vim-7.4a.tar.bz2

cd  ./vim74a

./configure --with-features=huge --enable-perlinterp --enable-pythoninterp --enable-rubyinterp --enable-cscope --enable-multibyte --disable-gui --prefix=${vim_install_dir}

VIMRUNTIMEDIR=${vim_install_dir}

make VIMRUNTIMEDIR=${VIMRUNTIMEDIR}

make install

cp -rf runtime/* ${vim_install_dir}/

# install vundle
install_dir="/home/maxingsong/.vim/bundle/vundle"

test -d ${install_dir} || mkdir -p ${install_dir}

git clone https://github.com/gmarik/vundle.git ${install_dir}

cd ${base_dir}
cat ./bundle.conf >> ~/.vimrc

vim +BundleInstall +qall

cd ~/.vim/bundle/YouCompleteMe

git submodule update --init --recursive

./install.sh --clang-completer
```
