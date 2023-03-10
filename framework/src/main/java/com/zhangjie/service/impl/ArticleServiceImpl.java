package com.zhangjie.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.github.jeffreyning.mybatisplus.service.MppServiceImpl;
import com.zhangjie.constants.SystemConstants;
import com.zhangjie.domain.dto.AddArticleDto;
import com.zhangjie.domain.entity.Article;
import com.zhangjie.domain.entity.ArticleTag;
import com.zhangjie.domain.entity.Category;
import com.zhangjie.domain.entity.Tag;
import com.zhangjie.domain.vo.*;
import com.zhangjie.mapper.ArticleMapper;
import com.zhangjie.service.ArticleService;
import com.zhangjie.service.ArticleTagService;
import com.zhangjie.service.CategoryService;
import com.zhangjie.service.TagService;
import com.zhangjie.utils.BeanCopyUtils;
import com.zhangjie.utils.RedisCache;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class ArticleServiceImpl extends ServiceImpl<ArticleMapper, Article> implements ArticleService {


    @Autowired
    RedisCache redisCache;
    @Autowired
    CategoryService categoryService;

    @Autowired
    ArticleTagService articleTagService;


    @Autowired
    TagService tagService;

    @Override
    public List<HotArticleVo> getHotArticleList() {
        //查询热门文章 封装成List<Article>返回
        LambdaQueryWrapper<Article> lambdaQueryWrapper =  new LambdaQueryWrapper<>();
        // 必须是正式文章
        lambdaQueryWrapper.eq(Article::getStatus, SystemConstants.ARTICLE_STATUS_NORMAL);
        // 按照浏览量进行排序
        lambdaQueryWrapper.orderByDesc(Article::getViewCount);
        // 最多查询10条
        Page<Article> page = new Page<>(SystemConstants.DEFULT_PAGE_CURRENT,SystemConstants.DEFULT_PAGE_SIZE);

        List<Article> articles = page(page, lambdaQueryWrapper).getRecords();

        List<HotArticleVo> hotArticleVos =  BeanCopyUtils.copyBeanList(articles, HotArticleVo.class);

        return hotArticleVos;
    }

    @Override
    public Object getArticleList(Integer pageNum, Integer pageSize, Long categoryId) {
        //查询条件
        LambdaQueryWrapper<Article> lambdaQueryWrapper = new LambdaQueryWrapper<>();
        //如果有categoryId 就要查询
        lambdaQueryWrapper.eq(Objects.nonNull(categoryId)&&categoryId>0,Article::getCategoryId,categoryId);
        //状态是正式发布的
        lambdaQueryWrapper.eq(Article::getStatus,SystemConstants.ARTICLE_STATUS_NORMAL);
        //对isTop进行降序排序
        lambdaQueryWrapper.orderByDesc(Article::getIsTop);

        //分页查询
        Page<Article> page = new Page<>(pageNum,pageSize);
        page(page, lambdaQueryWrapper);

        //查询categoryName
        List<Article> articles = page.getRecords();
        //此处流式处理以后返回的对象地址依然是page.getRecords()中获取到的地址
        articles.stream()
                .map(article -> article.setCategoryName(categoryService.getById(article.getCategoryId()).getName()))
                .map(article -> article.setViewCount(((Integer)redisCache.getCacheMapValue(SystemConstants.ARTICLE_VIEW_COUNT_REDIS_CACHE, article.getId().toString())).longValue()))
                .collect(Collectors.toList());

        List<ArticleListVo> articleListVos = BeanCopyUtils.copyBeanList(page.getRecords(), ArticleListVo.class);
        //封装查询结果
        return new PageVo(articleListVos,page.getTotal());
    }

    @Override
    public Object getArticleDetail(Long id) {
        //根据id查询文章
        Article article = getById(id);
        //从redis获取viewcount
        Integer viewCount = redisCache.getCacheMapValue(SystemConstants.ARTICLE_VIEW_COUNT_REDIS_CACHE, id.toString());
        article.setViewCount(viewCount.longValue());
        //转换成Vo
        ArticleDetailVo articleDetailVo = BeanCopyUtils.copyBean(article, ArticleDetailVo.class);
        //根据分类id查询分类名
        Category category = categoryService.getById(articleDetailVo.getCategoryId());
        if(Objects.nonNull(category)){
            articleDetailVo.setCategoryName(category.getName());
        }
        return articleDetailVo;
    }

    @Override
    public void updateViewCount(Long id) {
        //更新redis中对应id的浏览量
        redisCache.incrementCacheMapValue(SystemConstants.ARTICLE_VIEW_COUNT_REDIS_CACHE,id.toString(),1);
    }

    @Override
    public void addArticle(AddArticleDto articleDto) {
        //保存文章内容
        Article article = BeanCopyUtils.copyBean(articleDto, Article.class);
        save(article);

        //保存文章标签关联
        List<ArticleTag> articleTags = articleDto.getTags().stream()
                .map(tagId -> new ArticleTag(articleDto.getId(), tagId))
                .collect(Collectors.toList());

        articleTagService.saveBatch(articleTags);
    }

    @Override
    public Object getArticleList(Integer pageNum, Integer pageSize) {
        //
        Page page = new Page(pageNum, pageSize);
        LambdaQueryWrapper<Article> lambdaQueryWrapper = new LambdaQueryWrapper<>();
        lambdaQueryWrapper.eq(Article::getStatus,SystemConstants.ARTICLE_STATUS_NORMAL);

        page(page,lambdaQueryWrapper);
        List<ArticleDetailVo> articleDetailVos = BeanCopyUtils.copyBeanList(page.getRecords(), ArticleDetailVo.class);

        return new PageVo(articleDetailVos,page.getTotal());
    }

    @Override
    public Object getArticle(Long id) {
        //根据id查询文章
        Article article = getById(id);

        //根据文章id查询相关tagid
        LambdaQueryWrapper<ArticleTag> lambdaQueryWrapper = new LambdaQueryWrapper<>();
        lambdaQueryWrapper.select(ArticleTag::getTagId)
                .eq(ArticleTag::getArticleId,id);
        List<ArticleTag> tagIds = articleTagService.list(lambdaQueryWrapper);
        List<Long> list = tagIds.stream().map(ArticleTag -> ArticleTag.getTagId()).collect(Collectors.toList());
//        //根据tagId获取tagname
//        LambdaQueryWrapper<Tag> lambdaQueryWrapper1 = new LambdaQueryWrapper<>();
//        lambdaQueryWrapper1.select(Tag::getName).in(Tag::getId,list);
//        List<Tag> list1 = tagService.list(lambdaQueryWrapper1);
//        List<String> list2 = list1.stream().map(Tag -> Tag.getName()).collect(Collectors.toList());
        article.setTags(list);

        return article;
    }

    @Override
    public void updateArticle(AddArticleDto articleDto) {
        //保存文章内容
        Article article = BeanCopyUtils.copyBean(articleDto, Article.class);
        updateById(article);

        //保存文章标签关联
        List<ArticleTag> articleTags = articleDto.getTags().stream()
                .map(tagId -> new ArticleTag(articleDto.getId(), tagId))
                .collect(Collectors.toList());

        //TODO 此处没有更新tags
//        for(ArticleTag at:articleTags){
//            articleTagService.saveOrUpdate(at);
//        }

    }
}
