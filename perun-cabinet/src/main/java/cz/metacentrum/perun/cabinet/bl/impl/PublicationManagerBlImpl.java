package cz.metacentrum.perun.cabinet.bl.impl;

import java.util.Date;
import java.util.List;

import cz.metacentrum.perun.core.api.exceptions.InternalErrorException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import cz.metacentrum.perun.cabinet.dao.PublicationManagerDao;
import cz.metacentrum.perun.cabinet.model.Author;
import cz.metacentrum.perun.cabinet.model.Authorship;
import cz.metacentrum.perun.cabinet.model.Publication;
import cz.metacentrum.perun.cabinet.model.PublicationForGUI;
import cz.metacentrum.perun.cabinet.model.PublicationSystem;
import cz.metacentrum.perun.cabinet.model.Thanks;
import cz.metacentrum.perun.cabinet.bl.CabinetException;
import cz.metacentrum.perun.cabinet.bl.ErrorCodes;
import cz.metacentrum.perun.cabinet.bl.AuthorshipManagerBl;
import cz.metacentrum.perun.cabinet.bl.PerunManagerBl;
import cz.metacentrum.perun.cabinet.bl.PublicationManagerBl;
import cz.metacentrum.perun.cabinet.bl.PublicationSystemManagerBl;
import cz.metacentrum.perun.cabinet.bl.ThanksManagerBl;
import cz.metacentrum.perun.cabinet.bl.SortParam;
import cz.metacentrum.perun.core.api.PerunSession;
import cz.metacentrum.perun.core.api.Role;
import cz.metacentrum.perun.core.api.exceptions.PerunException;
import cz.metacentrum.perun.core.bl.PerunBl;
import cz.metacentrum.perun.core.api.AuthzResolver;

/**
 * Class for handling Publication entity in Cabinet.
 *
 * @author Jiri Harazim <harazim@mail.muni.cz>
 * @author Pavel Zlamal <256627@mail.muni.cz>
 */
public class PublicationManagerBlImpl implements PublicationManagerBl {

	private PublicationManagerDao publicationManagerDao;
	private AuthorshipManagerBl authorshipManagerBl;
	private PublicationSystemManagerBl publicationSystemManagerBl;
	private PerunManagerBl perunManagerBl;
	private ThanksManagerBl thanksManagerBl;

	@Autowired
	private PerunBl perun;

	// setters ----------------------------------------

	@Autowired
	public void setAuthorshipManagerBl(AuthorshipManagerBl authorshipManagerBl) {
		this.authorshipManagerBl = authorshipManagerBl;
	}

	@Autowired
	public void setPublicationManagerDao(PublicationManagerDao publicationManagerDao) {
		this.publicationManagerDao = publicationManagerDao;
	}

	@Autowired
	public void setPublicationSystemManagerBl(PublicationSystemManagerBl pubService) {
		this.publicationSystemManagerBl = pubService;
	}

	@Autowired
	public void setPerunManagerBl(PerunManagerBl perunManagerBl) {
		this.perunManagerBl = perunManagerBl;
	}

	@Autowired
	public void setThanksManagerBl(ThanksManagerBl thanksManagerBl) {
		this.thanksManagerBl = thanksManagerBl;
	}

	public PublicationManagerDao getPublicationManagerDao() {
		return publicationManagerDao;
	}

	public AuthorshipManagerBl getAuthorshipManagerBl() {
		return authorshipManagerBl;
	}

	public PublicationSystemManagerBl getPublicationSystemManagerBl() {
		return publicationSystemManagerBl;
	}

	public PerunManagerBl getPerunManagerBl() {
		return perunManagerBl;
	}

	public ThanksManagerBl getThanksManagerBl() {
		return thanksManagerBl;
	}

// business methods --------------------------------

	public int createPublication(PerunSession sess, Publication p) throws CabinetException, InternalErrorException {

		if (p.getCreatedDate() == null)
			p.setCreatedDate(new Date());
		if (p.getLocked() == null) {
			p.setLocked(false);
		}
		if (p.getCreatedByUid() == null && sess != null) {
			p.setCreatedByUid(sess.getPerunPrincipal().getUserId());
		}

		if (p.getExternalId() == 0 && p.getPublicationSystemId() == 0) {
			// check existence
			if (publicationExists(p)) {
				throw new CabinetException("Cannot create duplicate publication: "+p, ErrorCodes.PUBLICATION_ALREADY_EXISTS);
			}
			// get internal pub. system
			PublicationSystem ps = getPublicationSystemManagerBl().getPublicationSystemByName("INTERNAL");
			// There is only one internal system so, get(0) is safe
			p.setPublicationSystemId(ps.getId());
			//
			stripLongParams(p);
			// create internal
			return getPublicationManagerDao().createInternalPublication(sess, p);
		} else {
			if (publicationExists(p)) throw new CabinetException("Cannot create duplicate publication: "+p, ErrorCodes.PUBLICATION_ALREADY_EXISTS);

			stripLongParams(p);

			return getPublicationManagerDao().createPublication(sess, p);
		}
	}

	public boolean publicationExists(Publication p) {
		if (p.getId() != null && p.getId() !=0) {
			return getPublicationManagerDao().findPublicationById(p.getId()) != null;
		}
		if (p.getExternalId() != null && p.getExternalId() != 0 && p.getPublicationSystemId() != null && p.getPublicationSystemId() != 0) {
			Publication filter = new Publication();
			filter.setExternalId(p.getExternalId());
			filter.setPublicationSystemId(p.getPublicationSystemId());
			return getPublicationManagerDao().findPublicationsByFilter(filter).size() >= 1;
		}
		return false;
	}


	public List<Publication> findPublicationsByFilter(Publication p) {
		return getPublicationManagerDao().findPublicationsByFilter(p);
	}

	public List<PublicationForGUI> findRichPublicationsByFilter(Publication p, Integer userId) {
		return getPublicationManagerDao().findRichPublicationsByFilter(p, userId);
	}

	public List<PublicationForGUI> findRichPublicationsByGUIFilter(Publication p, Integer userId, int yearSince, int yearTill) {
		return getPublicationManagerDao().findRichPublicationsByGUIFilter(p, userId, yearSince, yearTill);
	}

	public Publication findPublicationById(Integer publicationId) {
		return getPublicationManagerDao().findPublicationById(publicationId);
	}

	public PublicationForGUI findRichPublicationById(Integer publicationId) {
		return getPublicationManagerDao().findRichPublicationById(publicationId);
	}

	public List<Publication> findAllPublications() {
		return getPublicationManagerDao().findAllPublications();
	}

	public List<PublicationForGUI> findAllRichPublications() {
		return getPublicationManagerDao().findRichPublicationsByGUIFilter(null, null, 0, 0);
	}

	public List<Publication> findPublicationsByFilter(Publication publication,
			SortParam sp) {
		if (sp == null) return findPublicationsByFilter(publication);
		if (! sp.getProperty().toString().matches("[a-z,A-Z,_,0-9]*")) throw new IllegalArgumentException("sortParam.property contains not allowed symbols: "+sp.getProperty());
		return getPublicationManagerDao().findPublicationsByFilter(publication, sp);
	}


	public int getPublicationsCount() {
		return getPublicationManagerDao().getPublicationsCount();
	}


	public int updatePublicationById(PerunSession sess, Publication publication) throws CabinetException, InternalErrorException {

		if (publication.getId() == null || publication.getExternalId() == null || publication.getPublicationSystemId() == null) {
			// such publication can't exists
			throw new CabinetException("Publication doesn't exists: "+publication, ErrorCodes.PUBLICATION_NOT_EXISTS);
		}

		// strip long params in new publication
		stripLongParams(publication);

		//don't create already existing publication (same id or externalId&&pubSysId)
		Publication filter = new Publication(); // for ext_id & pubSysId

		filter.setPublicationSystemId(publication.getPublicationSystemId());
		filter.setExternalId(publication.getExternalId());
		List<Publication> publications = findPublicationsByFilter(filter);

		if (publications.size() > 1) {
			throw new CabinetException("Consistency error: more than one unique publications found by ExtID and PubSysID.");
		}
		if (publications.size() > 0 && !(publication.getId().equals((publications.get(0).getId())))) {
			throw new CabinetException("Cannot update to duplicate publication: "+publication, ErrorCodes.PUBLICATION_ALREADY_EXISTS);
		}

		// save old pub (must be Rich to contain all authors)
		Publication oldPub = findPublicationById(publication.getId());

		// update publication in DB
		int result = getPublicationManagerDao().updatePublicationById(publication);

		// if updated and rank or category was changed
		if (result > 0 && ((oldPub.getRank() != publication.getRank()) || (oldPub.getCategoryId() != publication.getCategoryId()))) {
			// update coeficient for all it's authors
			List<Author> authors = getAuthorshipManagerBl().getAuthorsByPublicationId(oldPub.getId());
			for (Author a : authors) {
				getPerunManagerBl().updatePriorityCoefficient(sess, a.getId(), getAuthorshipManagerBl().calculateNewRank(a.getAuthorships()));
			}
		}

		return result;

	}

	public int deletePublicationById(PerunSession sess, Integer id) throws CabinetException {

		Publication pub = findPublicationById(id);
		if (pub == null) throw new CabinetException(ErrorCodes.PUBLICATION_NOT_EXISTS);

		// To delete publication user must be either PERUNADMIN
		// or user who created record (publication.createdBy==actor property)
		try {
			if (!AuthzResolver.isAuthorized(sess, Role.PERUNADMIN) &&
					!pub.getCreatedBy().equalsIgnoreCase(sess.getPerunPrincipal().getActor()) &&
					!pub.getCreatedByUid().equals(sess.getPerunPrincipal().getUserId())) {
				// not perun admin or author of record
				throw new CabinetException("You are not allowed to delete publications you didn't created.", ErrorCodes.NOT_AUTHORIZED);
					}
		} catch (PerunException pe) {
			throw new CabinetException(ErrorCodes.PERUN_EXCEPTION, pe);
		}

		// delete action
		try {

			// delete authors
			for (Authorship a : getAuthorshipManagerBl().getAuthorshipsByPublicationId(id)) {
				getAuthorshipManagerBl().deleteAuthorship(sess, a);
			}
			// delete thanks
			for (Thanks t : getThanksManagerBl().getThanksByPublicationId(id)) {
				getThanksManagerBl().deleteThanks(sess, t);
			}

			// delete publication
			if (AuthzResolver.isAuthorized(sess, Role.PERUNADMIN)) {

				// only perun admin can actually delete publication
				return getPublicationManagerDao().deletePublicationById(id);

			} else {

				return 1; // for others return as OK - perunadmin then deletes pubs manually

			}

		} catch (DataIntegrityViolationException ex) {
			throw new CabinetException("Can't delete publication with authors or thanks. Please remove them first in order to delete publication.", ErrorCodes.PUBLICATION_HAS_AUTHORS_OR_THANKS);
		} catch (PerunException ex) {
			throw new CabinetException(ErrorCodes.PERUN_EXCEPTION, ex);
		}

	}

	public int lockPublications(PerunSession sess, boolean lock, List<Publication> pubs) throws CabinetException {

		// AUTHZ
		try {
			if (!AuthzResolver.isAuthorized(sess, Role.PERUNADMIN)) {
				throw new CabinetException("lockPublications()", ErrorCodes.NOT_AUTHORIZED);
			}
		} catch (PerunException ex) {
			throw new CabinetException(ErrorCodes.PERUN_EXCEPTION, ex);
		}
		// check input
		if (pubs == null) {
			throw new NullPointerException("Publications to lock/unlock can't be null");
		}

		// GO
		return getPublicationManagerDao().lockPublications(lock, pubs);

	}

	/**
	 * Strip long params in publication object
	 * to prevent SQL errors on columns
	 *
	 * @param p publication to check
	 */
	protected void stripLongParams(Publication p) {

		if (p.getTitle() != null && p.getTitle().length() > 1024) {
			p.setTitle(p.getTitle().substring(0, 1024));
		}
		if (p.getMain() != null && p.getMain().length() > 4000) {
			p.setMain(p.getMain().substring(0, 4000));
		}
		if (p.getIsbn() != null && p.getIsbn().length() > 32) {
			p.setIsbn(p.getIsbn().substring(0, 32));
		}
		if (p.getDoi() != null && p.getDoi().length() > 256) {
			p.setDoi(p.getDoi().substring(0, 256));
		}
	}

}
