# blog.ftbb

Simple web site generator for:

[Flipping the Bozo Bit](http://ftbb.tv/)

The software generates the web site, then I hand copy it to a static
web server. That's it!

## Pre-requisites

**mmd**

    $ brew install multimarkdown

**webdev**

This is my custom web server for viewing the generated blog locally:

    $ git clone git@github.com/zentrope/sw-tools
    $ cd sw-tools
    $ make install   # Puts utils in ~/Bin

This requires that you've installed Xcode.app.

This particular web server is good for apps with synthetic routing in
that it serves `index.html` if refreshed with a route that doesn't
exist on the file system. This feature is not applicable here.

The fact that my `Makefile` depends on this is problematic, sure, but
I'm just having fun doing this in the first place. So, uh, this isn't
a model for good cross-project dependencies.

## License

Copyright (c) 2018-present Keith Irwin

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published
by the Free Software Foundation, either version 3 of the License,
or (at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program.  If not, see
[http://www.gnu.org/licenses/](http://www.gnu.org/licenses/).
