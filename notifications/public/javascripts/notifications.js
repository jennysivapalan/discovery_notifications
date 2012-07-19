console.log('here');

jQ.getScript(commonStaticRoot + "/scripts/mustache.js");

jQ("<link/>", {
   rel: "stylesheet",
   type: "text/css",
   href: commonStaticRoot+"/styles/notifications.css"
}).appendTo("head");


var notifications = (function() {
    var server = 'http://gnm41068.int.gnl:9000/';
    var toolbarHTML;
    jQ.support.cors = true;


    function init() {
        var self = this;
        jQ.ajax({
            url: commonStaticRoot+'scripts/notifications_template.html',
            async: false,
            success: setupPage
        });
    }

    function setupPage(templateHTML) {
        toolbarHTML = jQ(templateHTML).html();        
        getNotifications();
    }

    var getNotifications = function() {
        jQ.ajax({
            url: server + 'user/103?callback=?',
            data: {},
            dataType: 'jsonp',
            crossDomain: true,
            success: renderNotifications
        });
    };

    var renderNotifications = function(data, textStatus, jqXHR) {
        jQ('#notification-toolbar').remove();

        var notificationCount = data.subscribedBlogs.length;
        notificationCount += data.subscribedComments.length;
        notificationCount += data.subscribedTags.length;

        var toolbarData = {
            count: notificationCount,
            liveblogs: [],
            comments: [],
            tags: []
        };

        // Build blog notifications
        for (var i = 0; i<data.subscribedBlogs.length; i++) {
            console.log(data.subscribedBlogs[i].id);
            var notification = {
                id: data.subscribedBlogs[i].id,
                href: 'http://gnm41146.int.gnl/www.guardian.co.uk/' + data.subscribedBlogs[i].id,
                snippet: jQ(data.subscribedBlogs[i].trailText).text(),
                title: data.subscribedBlogs[i].title,
                thumbnail: data.subscribedBlogs[i].thumbnail,
                count: data.subscribedBlogs[i].count
            };

            toolbarData.liveblogs.push(notification);
        }

        // Build tag notifications
        for (var i = 0; i<data.subscribedTags.length; i++) {
            var notification = {
                id: data.subscribedTags[i].id,
                href: data.subscribedTags[i].content[0].webUrl,
                headline: data.subscribedTags[i].content[0].headline,
                author: data.subscribedTags[i].contributor,
                thumbnail: data.subscribedTags[i].content[0].thumbnail,
                snippet: data.subscribedTags[i].content[0].standfirst
            };

            toolbarData.tags.push(notification);
        }
        
        // Add notification to the page
        jQ(".top-navigation").append(Mustache.render(toolbarHTML, toolbarData));
        
        // Add event bindings
        bindNotifications();

        
        var currentBlogId = document.location.pathname.split('.co.uk/')[1];
        var isUserFollowingBlog = false;
        for (var x = 0; x < toolbarData.liveblogs.length; x++) {
            if (currentBlogId === toolbarData.liveblogs[x].id) {
                isUserFollowingBlog = true;
                break;
            }
        }
        addFollowBlogButton(isUserFollowingBlog);

        
        var currentTagId = jQ('a.contributor').attr('href').split('http://www.guardian.co.uk/')[1];
        var isUserFollowingTag = false;
        for (var g = 0; g < toolbarData.tags.length; g++) {
            if (currentTagId === toolbarData.tags[g].id) {
                isUserFollowingTag = true;
                break;
            }
        }
        addFollowTagButton(isUserFollowingTag);
        

        setTimeout(getNotifications, 10000);
    };



    function addFollowBlogButton(isFollowing) {
        var buttonText = (isFollowing) ? 'Unfollow blog' : 'Follow blog';
        var followButtonHTML = '<div class="followBtn followBlog">' + buttonText + '</div>';
        var followButtonData = {};
        jQ(".article-meta").before(Mustache.render(followButtonHTML, followButtonData));
        
        if (isFollowing) {
            jQ('.followBtn.followTag').click(followBlog);
        } else {
            jQ('.followBtn.followTag').click(unFollowBlog);
        }
    }



    function addFollowTagButton(isFollowing) {
        var buttonText = (isFollowing) ? 'Unfollow' : 'Follow';
        var followButtonHTML = '<div class="followBtn followTag">' + buttonText + '</div>';
        var followButtonData = {};
        jQ('a.contributor').after(Mustache.render(followButtonHTML, followButtonData));
        
        if (isFollowing) {
            jQ('.followBtn.followTag');
        };
    }


    var bindFollowButton = function() {
        var followBtn = jQ('#followBtn');
        followBtn.click(followContent);
    };




    var followBlog = function() {
        var id = document.location.pathname.split('.co.uk/')[1];
        console.log('following blog ', id);
        jQ.ajax({
            url : server + '/user/103/blog',
            crossDomain: true,
            type: 'POST',
            dataType: 'json',
            data: {
                path: id,
                lastViewedId: jQ('.block:first').data('id')
            },
            success: function(response) {
                cosole.log(response);
            }
        });
    };

    var unFollowBlog = function() {
        var id = document.location.pathname.split('.co.uk/')[1];
        console.log('unfollowing blog', id);
         jQ.ajax({
            url: server + 'user/103/blog',
            crossDomain: true,
            type: 'POST',
            dataType: 'json',
            data: {
                blog: id
            },
            success: function(response) {
                cosole.log(response);
            }
        });
    //         http://gnm41097.int.gnl:9000/user/:id/unsubscribeFromBlog   
    // with parameter blog (i.e. blog=<path>)
    };

    var clearNotification = function(id) {
        var data = {
            // what data?
        };

        jQ.post(
            server + 'user/103/clearCommentNotification',
            data,
            function() {}
        );
    };

    

    var bindNotifications = function() {
        jQ('#notification-count').click(function() {
            jQ('#notification-items').toggle();
        });

        jQ('.notification-clear').click(function(e) {
            e.stopPropagation();
            jQ('#notification-count').text(
                parseInt(jQ('#notification-count').text(), 10) - 1
            );

            jQ(this).parent('.notification-item').slideUp(function() {
                jQ(this).remove();
            });
        });

        jQ('.notification-item').click(function() {
            window.location = jQ(this).attr('href');
        });

        jQ('.notifications-clear-all').click(function() {
            jQ('#notification-items').hide();
            jQ('.notification-item').remove();
        });
    };

    var addFollowButton = function() {

    };

    return {
        init: init
    };

}());


// Add notifcations when page has finished loading
jQ('document').ready(notifications.init);
