本节主要介绍我所安装的插件
============

##vundle##

vundle主要用来管理vim的插件，集成了git，只要你能在github上找到相应的插件，就可以简单的对插件进行安装，卸载。

我的vundle的配置
```sh
set nocompatible"{{{
filetype off
set rtp+=~/.vim/bundle/vundle/
call vundle#rc()

Bundle 'gmarik/vundle'
Bundle 'vim-scripts/DoxygenToolkit.vim'
Bundle 'vim-scripts/mru.vim'
Bundle 'tpope/vim-surround'
Bundle 'Townk/vim-autoclose'
Bundle 'scrooloose/syntastic'
Bundle 'kien/ctrlp.vim'
Bundle 'Lokaltog/vim-easymotion'
Bundle 'vim-scripts/taglist.vim'
Bundle 'scrooloose/nerdcommenter'
Bundle 'duganchen/vim-soy'
Bundle 'bronson/vim-trailing-whitespace'
Bundle 'scrooloose/nerdtree'
Bundle 'majutsushi/tagbar'
Bundle 'jlanzarotta/bufexplorer'
Bundle 'fholgado/minibufexpl.vim'
Bundle 'kien/rainbow_parentheses.vim'
Bundle 'tomasr/molokai'
Bundle 'Yggdroot/indentLine'
Bundle 'altercation/vim-colors-solarized'
Bundle 'mhinz/vim-startify'
Bundle 'vim-scripts/snipMate'
Bundle 'vim-scripts/echofunc.vim'
" This plugin needs compilation
Bundle 'Valloric/YouCompleteMe'
" Bundle 'mattn/zencoding-vim'
" Bundle 'rstacruz/sparkup', {'rtp': 'vim/'}
" Bundle 'SirVer/ultisnips'
" Bundle 'tpope/vim-speeddating'"}}}
```

##DoxygenToolkit##

DoxygenToolkit主要用于根据配置自动生成注释

我的DoxygenToolkit的配置
```sh
" Loading DoxygenToolkit
let g:DoxygenToolkit_authorName="maxingsong, maxingsong@xunlei.com"
let s:licenseTag = "Copyright(C) "
let s:licenseTag = s:licenseTag . "xunlei"
let s:licenseTag = s:licenseTag . "All right reserved\<enter>"
let g:DoxygenToolkit_licenseTag = s:licenseTag
let g:DoxygenToolkit_briefTag_funcame="yes"
let g:doxygen_enhanced_color=1
let g:DoxygenToolkit_commentType="C++"
```

##mru.vim##

mru保存最近使用的文件，配置如下：
```sh
" mru
let MRU_Window_Height = 10
nmap <Leader>r :MRU<cr>
```

##vim-autoclose

vim-autoclose主要用于自动的添加相匹配的括号，如输入(，自动添加)

##scrooloose/syntastic##

检查语法错误，暂时没有使用

##ctrlp##

ctrlp主要用于查找文件，<c+f>或者<c+b>用于选择模式，<c+d>用于选择文件名, <c+y>用于添加新的文件
```sh
" ctrlp
let g:ctrlp_map = '<c-p>'
set wildignore+=*/tmp/*,*.so,*.swp,*.zip,*.o,*.d
let g:ctrlp_custom_ignore = {
   \ 'dir': '\v[\/]\.(git|hg|svn)$',
   \ 'file': '\v\.(log|jpg|png|jpeg)$',
   \ }
```

##taglist##

taglist主要用于显示代码结构概览。

```sh
" taglist
let g:Tlist_WinWidth = 25"{{{
let g:Tlist_Use_Right_Window = 0
let g:Tlist_Auto_Update = 1
let g:Tlist_Process_File_Always = 1
let g:Tlist_Exit_OnlyWindow = 1
let g:Tlist_Show_One_File = 1
let g:Tlist_Enable_Fold_Column = 0
let g:Tlist_Auto_Highlight_Tag = 1
let g:Tlist_GainFocus_On_ToggleOpen = 1
nmap <Leader>t :TlistToggle<cr>
" tl
map tl :Tlist<CR><c-l>"}}}
```

##nerdcommenter##

nerdcommenter主要用于注释代码

```sh
let g:NERDSpaceDelims = 1
```

##vim-trailing-whitespace##

vim-trailing-whitespace主要用于高亮行尾多余的空格

##nerdtree##

列举文件信息

##YouCompleteMe##

YouCompleteMe是一个代码自动补全的插件

```c
let g:ycm_list_select_completion=['<C-TAB>','<Down>']
let g:ycm_key_list_previous_completion=['<C-S-TAB>', '<Up>']
let g:ycm_key_invoke_completion=''
let g:ycm_confirm_extra_conf=0
" let g:ycm_seed_identifiers_with_syntax=1
let g:ycm_min_num_of_chars_for_completion=1
let g:ycm_cache_omnifunc=0
" let g:ycm_semantic_triggers = {}
" let g:ycm_semantic_triggers.c = ['->', '.', ' ', '(', '[', '&']
" set completeopt-=preview
```
