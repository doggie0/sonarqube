#
# SonarQube, open source software quality management tool.
# Copyright (C) 2008-2014 SonarSource
# mailto:contact AT sonarsource DOT com
#
# SonarQube is free software; you can redistribute it and/or
# modify it under the terms of the GNU Lesser General Public
# License as published by the Free Software Foundation; either
# version 3 of the License, or (at your option) any later version.
#
# SonarQube is distributed in the hope that it will be useful,
# but WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
# Lesser General Public License for more details.
#
# You should have received a copy of the GNU Lesser General Public License
# along with this program; if not, write to the Free Software Foundation,
# Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
#
require 'cgi'
require 'java'

class RulesConfigurationController < ApplicationController

  SECTION=Navigation::SECTION_QUALITY_PROFILES

  STATUS_ACTIVE = "ACTIVE"
  STATUS_INACTIVE = "INACTIVE"

  ANY_SELECTION = []
  RULE_PRIORITIES = Sonar::RulePriority.as_options.reverse


  #
  #
  # POST /rules_configuration/revert_rule?id=<profile id>&active_rule_id=<active rule id>
  #
  #
  def revert_rule
    verify_post_request
    require_parameters :id, :active_rule_id

    active_rule_id = params[:active_rule_id].to_i
    call_backend do
      Internal.quality_profiles.revertActiveRule(active_rule_id)
    end

    redirect_to request.query_parameters.merge({:action => 'index', :id => params[:id].to_i, :commit => nil})
  end


  #
  #
  # POST /rules_configuration/activate_rule?id=<profile id>&rule_id=<rule id>&level=<priority>
  #
  # If the parameter "level" is blank or null, then the rule is removed from the profile.
  #
  #
  def activate_rule
    verify_post_request
    require_parameters :id, :rule_id

    rule = nil
    profile_id = params[:id].to_i
    rule_id = params[:rule_id].to_i
    call_backend do
      severity = params[:level]
      if severity.blank?
        # deactivate the rule
        Internal.quality_profiles.deactivateRule(profile_id, rule_id)
        rule = Internal.quality_profiles.findByRule(rule_id)
      else
        # activate the rule
        Internal.quality_profiles.activateRule(profile_id, rule_id, severity)
        rule = Internal.quality_profiles.findByProfileAndRule(profile_id, rule_id)
      end
    end

    profile = Internal.quality_profiles.profile(profile_id)
    parent_profile = Internal.quality_profiles.parent(profile)

    render :update do |page|
      page.replace_html("rule_#{rule.id}", :partial => 'rule', :object => rule, :locals => {:rule => rule, :profile => profile, :parent_profile => parent_profile})
      page.assign('localModifications', true)
    end
  end


  #
  #
  # GET /rules_configuration/new/<profile id>?rule_id=<rule id>
  #
  #
  def new
    # form to duplicate a rule
    require_parameters :id, :rule_id
    @profile = Internal.quality_profiles.profile(params[:id].to_i)
    not_found('Profile not found') unless @profile
    add_breadcrumbs ProfilesController::root_breadcrumb, Api::Utils.language_name(@profile.language),
                    {:name => @profile.name, :url => {:controller => 'rules_configuration', :action => 'index', :id => @profile.id}}

    @rule = Internal.quality_profiles.findByRule(params[:rule_id].to_i)
  end


  # deprecated since 2.3
  def export
    redirect_to request.query_parameters.merge({:controller => 'profiles', :action => 'export'})
  end

  #
  #
  # GET /rules_configuration/new/<profile id>?rule_id=<rule id>
  #
  #
  def edit
    # form to edit a rule
    require_parameters :id, :rule_id

    call_backend do
      @profile = Internal.quality_profiles.profile(params[:id].to_i)
      not_found('Profile not found') unless @profile
      @rule = Internal.quality_profiles.findByRule(params[:rule_id].to_i)
      if @rule.templateId().nil?
        redirect_to :action => 'index', :id => params[:id]
      else
        @parent_rule = Internal.quality_profiles.findByRule(@rule.templateId())
        @active_rules = Internal.quality_profiles.countActiveRules(@rule.id()).to_i
      end
    end
  end


  #
  #
  # POST /rules_configuration/bulk_edit?id=<profile id>&&bulk_action=<action>
  #
  # Values of the parameter 'bulk_action' :
  #   - 'activate' : activate all the selected rules with their default priority
  #   - 'deactivate' : deactivate all the selected rules
  #
  #
  def bulk_edit
    verify_post_request
    access_denied unless has_role?(:profileadmin)
    require_parameters :id, :bulk_action

    stop_watch = Internal.profiling.start("rules", "BASIC")
    @profile = Internal.quality_profiles.profile(params[:id].to_i)
    not_found('Profile not found') unless @profile
    init_params
    criteria = init_criteria
    query = Java::OrgSonarServerQualityprofile::ProfileRuleQuery::parse(criteria.to_java)
    activation = params[:rule_activation] || STATUS_ACTIVE
    case params[:bulk_action]
      when 'activate'
        count = Internal.quality_profiles.bulkActivateRule(query)
        stop_watch.stop("Activate #{count} rules with criteria #{criteria.to_json}")

        flash[:notice]=message('rules_configuration.x_rules_have_been_activated', :params => count)
        activation=STATUS_ACTIVE if activation==STATUS_INACTIVE

      when 'deactivate'
        count = Internal.quality_profiles.bulkDeactivateRule(query)
        stop_watch.stop("Deactivate #{count} rules with criteria #{criteria.to_json}")

        flash[:notice]=message('rules_configuration.x_rules_have_been_deactivated', :params => count)
        activation=STATUS_INACTIVE if activation==STATUS_ACTIVE
    end

    url_parameters=request.query_parameters.merge({:action => 'index', :bulk_action => nil, :id => @profile.id, :rule_activation => activation})
    redirect_to url_parameters
  end

  private

  def init_params
    @id = params[:id]
    @priorities = filter_any(params[:priorities]) || ['']
    @repositories = filter_any(params[:repositories]) || ['']
    @activation = params[:rule_activation] || STATUS_ACTIVE
    @inheritance = params[:inheritance] || 'any'
    @status = params[:status]
    @tags = filter_any(params[:tags]) || ['']
    @sort_by = !params[:sort_by].blank? ? params[:sort_by] : Rule::SORT_BY_RULE_NAME
    @searchtext = params[:searchtext]
  end

  def filter_any(array)
    if array && array.size>1 && array.include?('')
      array=[''] #keep only 'any'
    end
    array
  end

  def init_criteria()
    if @sort_by == Rule::SORT_BY_RULE_NAME
      asc = true
    elsif @sort_by == Rule::SORT_BY_CREATION_DATE
      asc = false
    else
      asc = true
    end
    {"profileId" => @profile.id.to_i, "activation" => @activation, "severities" => @priorities, "inheritance" => @inheritance, "statuses" => @status,
     "repositoryKeys" => @repositories, "nameOrKey" => @searchtext, "include_parameters_and_notes" => true, "language" => @profile.language, "tags" => @tags,
     "sort_by" => @sort_by, "asc" => asc}
  end

  def criteria_params
    new_params = params.clone
    new_params.delete('controller')
    new_params.delete('action')
    new_params
  end

  def tag_selection_for_rule(rule)
    Internal.rule_tags.listAllTags().to_a.sort.collect do |tag|
      {
        :value => tag,
        :selected => (rule.systemTags.contains?(tag) || rule.adminTags.contains?(tag)),
        :read_only => rule.systemTags.contains?(tag)
      }
    end
  end

end