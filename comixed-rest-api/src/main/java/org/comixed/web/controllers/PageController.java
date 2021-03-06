/*
 * ComiXed - A digital comic book library management application.
 * Copyright (C) 2017, The ComiXed Project
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.package
 * org.comixed;
 */

package org.comixed.web.controllers;

import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Optional;

import org.comixed.library.model.BlockedPageHash;
import org.comixed.library.model.Comic;
import org.comixed.library.model.Page;
import org.comixed.library.model.PageType;
import org.comixed.library.model.View;
import org.comixed.library.model.View.PageList;
import org.comixed.library.utils.FileTypeIdentifier;
import org.comixed.repositories.BlockedPageHashRepository;
import org.comixed.repositories.ComicRepository;
import org.comixed.repositories.PageRepository;
import org.comixed.repositories.PageTypeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.annotation.JsonView;

@RestController
@RequestMapping("/api")
public class PageController
{
    protected final Logger logger = LoggerFactory.getLogger(this.getClass());

    @Autowired
    private ComicRepository comicRepository;

    @Autowired
    private PageRepository pageRepository;

    @Autowired
    private PageTypeRepository pageTypeRepository;

    @Autowired
    private BlockedPageHashRepository blockedPageHashRepository;

    @Autowired
    private FileTypeIdentifier fileTypeIdentifier;

    @RequestMapping(value = "/pages/blocked",
            method = RequestMethod.POST)
    public void addBlockedPageHash(@RequestParam("hash") String hash)
    {
        BlockedPageHash existing = this.blockedPageHashRepository.findByHash(hash);

        if (existing != null)
        {
            this.logger.debug("Blocked page hash already exists: {}", hash);
            return;
        }

        this.logger.debug("Creating new blocked page hash: {}", hash);
        existing = new BlockedPageHash(hash);
        this.blockedPageHashRepository.save(existing);
    }

    @RequestMapping(value = "/pages/hash/{hash}",
            method = RequestMethod.DELETE)
    public int deleteAllWithHash(@PathVariable("hash") String hash)
    {
        this.logger.debug("Marking as deleted all pages with hash={}", hash);

        int result = this.pageRepository.updateDeleteOnAllWithHash(hash, true);

        this.logger.debug("Marked {} pages", result);

        return result;
    }

    @RequestMapping(value = "/pages/{id}",
            method = RequestMethod.DELETE)
    public boolean deletePage(@PathVariable("id") long id)
    {
        this.logger.debug("Marking page as deleted: id={}", id);

        Optional<Page> record = this.pageRepository.findById(id);

        if (!record.isPresent())
        {
            this.logger.error("No such page: id={}", id);
            return false;
        } else
        {
            Page page = record.get();

            page.markDeleted(true);
            this.pageRepository.save(page);
            this.logger.debug("Page deleted: id={}", id);
            return true;
        }
    }

    @RequestMapping(value = "/comics/{id}/pages",
            method = RequestMethod.GET)
    @JsonView(PageList.class)
    public List<Page> getAll(@PathVariable("id") long id)
    {
        this.logger.debug("Getting all pages for comic: id={}", id);

        return this.getPagesForComic(id);
    }

    @RequestMapping(value = "/pages/blocked",
            method = RequestMethod.GET)
    public String[] getAllBlockedPageHashes()
    {
        this.logger.debug("Getting all blocked page hashes");

        String[] result = this.blockedPageHashRepository.getAllHashes();

        this.logger.debug("Returning {} hash(es)", result.length);

        return result;
    }

    @RequestMapping(value = "/pages/duplicates",
            method = RequestMethod.GET)
    @JsonView(View.PageList.class)
    public List<Page> getDuplicatePages()
    {
        this.logger.debug("Fetching the list of duplicate page hashes");

        List<Page> result = this.pageRepository.getDuplicatePages();

        this.logger.debug("Retrieved {} hashes", result.size());

        return result;
    }

    @RequestMapping(value = "/comics/{id}/pages/{index}/content",
            method = RequestMethod.GET)
    public ResponseEntity<byte[]> getImageInComicByIndex(@PathVariable("id") long id, @PathVariable("index") int index)
    {
        this.logger.debug("Getting the image for comic: id={} index={}", id, index);

        Optional<Comic> record = this.comicRepository.findById(id);

        if (record.isPresent()
                && (index < record.get().getPageCount()))
            return this.getResponseEntityForPage(record.get().getPage(index));

        if (!record.isPresent())
        {
            this.logger.debug("No such comic: id={}", id);
        } else
        {
            this.logger.debug("No such page: index={} page count={}", index, record.get().getPageCount());
        }

        return null;
    }

    @RequestMapping(value = "/pages/{id}/content",
            method = RequestMethod.GET)
    public ResponseEntity<byte[]> getPageContent(@PathVariable("id") long id)
    {
        this.logger.debug("Getting page: id={}", id);

        Optional<Page> record = this.pageRepository.findById(id);

        if (!record.isPresent())
        {
            this.logger.debug("No such page: id={}", id);
            return null;
        }

        return this.getResponseEntityForPage(record.get());
    }

    @RequestMapping(value = "/comics/{comic_id}/pages/{index}",
            method = RequestMethod.GET)
    public Page getPageInComicByIndex(@PathVariable("comic_id") long comicId, @PathVariable("index") int index)
    {
        this.logger.debug("Getting page for comic: id={} page={}", comicId, index);

        Optional<Comic> record = this.comicRepository.findById(comicId);

        if ((record.isPresent()) && (index < record.get().getPageCount())) return record.get().getPage(index);

        return null;
    }

    private List<Page> getPagesForComic(long id)
    {
        this.logger.debug("Getting pages for comic: id={}", id);
        Optional<Comic> record = this.comicRepository.findById(id);
        if (!record.isPresent())
        {
            this.logger.debug("No such comic found: id={}", id);

            return null;
        } else return record.get().getPages();
    }

    @RequestMapping(value = "/pages/types",
            method = RequestMethod.GET)
    public Iterable<PageType> getPageTypes()
    {
        this.logger.debug("Returning page types");
        return this.pageTypeRepository.findAll();
    }

    private ResponseEntity<byte[]> getResponseEntityForPage(Page page)
    {
        byte[] content = page.getContent();
        String type = this.fileTypeIdentifier.typeFor(new ByteArrayInputStream(content)) + "/"
                + this.fileTypeIdentifier.subtypeFor(new ByteArrayInputStream(content));
        return ResponseEntity.ok().contentLength(content.length)
                .header("Content-Disposition", "attachment; filename=\"" + page.getFilename() + "\"")
                .contentType(MediaType.valueOf(type)).body(content);
    }

    @RequestMapping(value = "/pages/blocked/{hash}",
            method = RequestMethod.DELETE)
    public void removeBlockedPageHash(@PathVariable("hash") String hash)
    {
        BlockedPageHash existing = this.blockedPageHashRepository.findByHash(hash);

        if (existing == null)
        {
            this.logger.debug("No such blocked page hash: {}", hash);
            return;
        }

        this.logger.debug("Removing blocked page hash: {}", hash);
        this.blockedPageHashRepository.delete(existing);
    }

    @RequestMapping(value = "/pages/hash/{hash}",
            method = RequestMethod.PUT)
    public int undeleteAllWithHash(@PathVariable("hash") String hash)
    {
        this.logger.debug("Marking as undeleted all pages with hash={}", hash);

        int result = this.pageRepository.updateDeleteOnAllWithHash(hash, false);

        this.logger.debug("Unmarked {} pages", result);

        return result;
    }

    @RequestMapping(value = "/pages/{id}/undelete",
            method = RequestMethod.POST)
    public boolean undeletePage(@PathVariable("id") long id)
    {
        this.logger.debug("Marking page as undeleted: id={}", id);

        Optional<Page> record = this.pageRepository.findById(id);

        if (!record.isPresent())
        {
            this.logger.error("No such page: id={}", id);
            return false;
        } else
        {
            Page page = record.get();
            page.markDeleted(false);
            this.pageRepository.save(page);
            this.logger.debug("Page undeleted: id={}", id);
            return true;
        }
    }

    @RequestMapping(value = "/pages/{id}/type",
            method = RequestMethod.PUT)
    public void updateTypeForPage(@PathVariable("id") long id, @RequestParam("type_id") long pageTypeId)
    {
        this.logger.debug("Setting page type: id={} typeId={}", id, pageTypeId);

        Optional<Page> pageRecord = this.pageRepository.findById(id);

        if (!pageRecord.isPresent())
        {
            this.logger.error("No such page: id={}", id);
        } else
        {
            Page page = pageRecord.get();
            Optional<PageType> typeRecord = this.pageTypeRepository.findById(pageTypeId);

            if (!typeRecord.isPresent())
            {
                this.logger.error("No such page type: typeId={}", pageTypeId);
            } else
            {
                PageType pageType = typeRecord.get();
                this.logger.debug("Updating page type");
                page.setPageType(pageType);
                this.pageRepository.save(page);
            }
        }
    }
}
