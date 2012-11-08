package org.kercheval.gradle.vcs;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.api.errors.ConcurrentRefUpdateException;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.InvalidRefNameException;
import org.eclipse.jgit.api.errors.InvalidTagNameException;
import org.eclipse.jgit.api.errors.NoHeadException;
import org.eclipse.jgit.api.errors.RefAlreadyExistsException;
import org.eclipse.jgit.api.errors.RefNotFoundException;
import org.eclipse.jgit.errors.NoWorkTreeException;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.RepositoryBuilder;
import org.eclipse.jgit.revwalk.RevTag;
import org.eclipse.jgit.revwalk.RevWalk;
import org.gradle.api.logging.Logger;
import org.kercheval.gradle.util.SortedProperties;

//
// This class implements the VCSAccess interface for GIT.
//
public class VCSGitImpl
	implements IVCSAccess
{
	static Object singletonLock = new Object();
	static VCSGitImpl singleton = null;

	public static IVCSAccess getInstance(final File srcRootDir, final Logger logger)
	{
		synchronized (singletonLock)
		{
			VCSGitImpl rVal = singleton;

			if (null == rVal)
			{
				rVal = new VCSGitImpl(srcRootDir, logger);
			}

			return rVal;
		}
	}

	private final File srcRootDir;

	private final Logger logger;

	private VCSGitImpl(final File srcRootDir, final Logger logger)
	{
		this.srcRootDir = srcRootDir;
		this.logger = logger;
	}

	@Override
	public void createBranch(final String branchName, final String remoteOrigin,
		final boolean ignoreOrigin)
		throws VCSException
	{
		// TODO: Need pull on branch from origin
		// TODO: Need push on branch from origin

		Repository repository = null;

		try
		{
			repository = new RepositoryBuilder().readEnvironment().findGitDir(getSrcRootDir())
				.build();

			final Git git = new Git(repository);

			git.branchCreate().setName(branchName).setForce(false).call();

			// 0) First check to see if refs/heads/<branchname> exists
			// If not, the do 1) otherwise do 2)
			// 1) First check to see if refs/remotes/origin/<branchname> exists
			//
			// Use code like
			//
			// may be able to use getRef instead (which returns null if not present)
			//
			// final Map<String, Ref> refMap = repository.getAllRefs();
			// if (refMap.containsKey("refs/remotes/origin/" + branchName))
			// {
			// // Do
			// // git branch branchname origin/branchname
			// }
			// else
			// {
			// // Do
			// // git branch branchname
			// // git push origin branchname
			// }
			// 2) Actually, should check for current local and current remote and
			// deal with repair logic. If remote and not local, then get.
			// If local and not remote then push. If neither then create and push.
		}
		catch (final IOException e)
		{
			throw new VCSException("Unable to find repository at: " + getSrcRootDir(), e);
		}
		catch (final RefAlreadyExistsException e)
		{
			throw new VCSException("Unable to create branch: " + branchName, e);
		}
		catch (final RefNotFoundException e)
		{
			throw new VCSException("Unable to create branch: " + branchName, e);
		}
		catch (final InvalidRefNameException e)
		{
			throw new VCSException("Unable to create branch: " + branchName, e);
		}
		catch (final GitAPIException e)
		{
			throw new VCSException("Unable to create branch: " + branchName, e);
		}
		finally
		{
			if (null != repository)
			{
				repository.close();
			}
		}
	}

	@Override
	public List<VCSTag> getAllTags()
		throws VCSException
	{
		return getTags(".*");
	}

	@Override
	public String getBranchName()
		throws VCSException
	{
		String rVal = "";
		Repository repository = null;

		try
		{
			repository = new RepositoryBuilder().readEnvironment().findGitDir(getSrcRootDir())
				.build();
			rVal = repository.getBranch();
		}
		catch (final IOException e)
		{
			throw new VCSException("Unable to find repository at: " + getSrcRootDir(), e);
		}
		finally
		{
			if (null != repository)
			{
				repository.close();
			}
		}

		return rVal;
	}

	@Override
	public SortedProperties getInfo()
		throws VCSException
	{
		final SortedProperties props = new SortedProperties();
		Repository repository = null;

		try
		{
			repository = new RepositoryBuilder().readEnvironment().findGitDir(getSrcRootDir())
				.build();
			props.addProperty("vcs.type", IVCSAccess.Type.GIT);
			props.addProperty("vcs.git.basedir", repository.getDirectory().getCanonicalPath());
			props.addProperty("vcs.git.branch", repository.getBranch());

			final ObjectId head = repository.resolve("HEAD");

			props.addProperty("vcs.git.last.commit", head.getName());

			final Config config = repository.getConfig();

			props.addProperty("vcs.git.user.name", config.getString("user", null, "name"));
			props.addProperty("vcs.git.user.email", config.getString("user", null, "email"));
			props.addProperty("vcs.git.remote.origin", config.getString("remote", "origin", "url"));

			try
			{
				final Status status = new Git(repository).status().call();

				props.addProperty("vcs.git.workspace.clean", Boolean.toString(status.isClean()));
				props.addProperty("vcs.git.workspace.files.added", status.getAdded().toString());
				props
					.addProperty("vcs.git.workspace.files.changed", status.getChanged().toString());
				props
					.addProperty("vcs.git.workspace.files.missing", status.getMissing().toString());
				props
					.addProperty("vcs.git.workspace.files.removed", status.getRemoved().toString());
				props.addProperty("vcs.git.workspace.files.untracked", status.getUntracked()
					.toString());
				props.addProperty("vcs.git.workspace.files.conflicting", status.getConflicting()
					.toString());
				props.addProperty("vcs.git.workspace.files.modified", status.getModified()
					.toString());
			}
			catch (final NoWorkTreeException e)
			{
				throw new VCSException("Unable to determine repository status", e);
			}
			catch (final GitAPIException e)
			{
				throw new VCSException("Unable to determine repository status", e);
			}
		}
		catch (final IOException e)
		{
			throw new VCSException("Unable to find repository at: " + getSrcRootDir(), e);
		}
		finally
		{
			if (null != repository)
			{
				repository.close();
			}
		}

		return props;
	}

	public Logger getLogger()
	{
		return logger;
	}

	public File getSrcRootDir()
	{
		return srcRootDir;
	}

	@Override
	public VCSStatus getStatus()
		throws VCSException
	{
		final VCSStatus rVal = new VCSStatus();
		Repository repository = null;

		try
		{
			repository = new RepositoryBuilder().readEnvironment().findGitDir(getSrcRootDir())
				.build();

			try
			{
				final Status status = new Git(repository).status().call();

				rVal.setAdded(status.getAdded());
				rVal.setChanged(status.getChanged());
				rVal.setMissing(status.getMissing());
				rVal.setRemoved(status.getRemoved());
				rVal.setUntracked(status.getUntracked());
				rVal.setConflicting(status.getConflicting());
				rVal.setModified(status.getModified());
			}
			catch (final NoWorkTreeException e)
			{
				throw new VCSException("Unable to determine repository status", e);
			}
			catch (final GitAPIException e)
			{
				throw new VCSException("Unable to determine repository status", e);
			}
		}
		catch (final IOException e)
		{
			throw new VCSException("Unable to find repository at: " + getSrcRootDir(), e);
		}
		finally
		{
			if (null != repository)
			{
				repository.close();
			}
		}

		return rVal;
	}

	@Override
	public List<VCSTag> getTags(final String regexFilter)
		throws VCSException
	{
		final List<VCSTag> rVal = new ArrayList<VCSTag>();
		Repository repository = null;

		try
		{
			repository = new RepositoryBuilder().readEnvironment().findGitDir(getSrcRootDir())
				.build();

			final Map<String, Ref> tags = repository.getTags();

			for (final String name : tags.keySet())
			{
				if (name.matches(regexFilter))
				{
					final Ref ref = tags.get(name);
					final RevWalk revWalk = new RevWalk(repository);

					try
					{
						final RevTag revTag = revWalk.parseTag(ref.getObjectId());

						if (null != revTag)
						{
							final PersonIdent ident = revTag.getTaggerIdent();

							if (null != ident)
							{
								rVal.add(new VCSTag(revTag.getTagName(), revTag.getName(), revTag
									.getFullMessage(), ident.getName(), ident.getEmailAddress(),
									ident.getWhen()));
							}
						}
					}
					finally
					{
						if (null != revWalk)
						{
							revWalk.dispose();
						}
					}
				}
			}
		}
		catch (final IOException e)
		{
			throw new VCSException("Unable to find repository at: " + getSrcRootDir(), e);
		}
		finally
		{
			if (null != repository)
			{
				repository.close();
			}
		}

		return rVal;
	}

	@Override
	public Type getType()
	{
		return IVCSAccess.Type.GIT;
	}

	@Override
	public void createTag(final VCSTag tag)
		throws VCSException
	{
		Repository repository = null;

		try
		{
			repository = new RepositoryBuilder().readEnvironment().findGitDir(getSrcRootDir())
				.build();

			final Git git = new Git(repository);

			try
			{
				git.tag().setName(tag.getName()).setMessage(tag.getComment()).call();
			}
			catch (final ConcurrentRefUpdateException e)
			{
				throw new VCSException("Unable to create tag: " + tag.getName(), e);
			}
			catch (final InvalidTagNameException e)
			{
				throw new VCSException("Unable to create tag: " + tag.getName(), e);
			}
			catch (final NoHeadException e)
			{
				throw new VCSException("Unable to create tag: " + tag.getName(), e);
			}
			catch (final GitAPIException e)
			{
				throw new VCSException("Unable to create tag: " + tag.getName(), e);
			}
		}
		catch (final IOException e)
		{
			throw new VCSException("Unable to find repository at: " + getSrcRootDir(), e);
		}
		finally
		{
			if (null != repository)
			{
				repository.close();
			}
		}
	}
}
